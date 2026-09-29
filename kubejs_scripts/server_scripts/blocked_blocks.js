// Furnace is gated behind the LV tier's stage - read from the shared progression
// config rather than hardcoding the stage id, see config_files/s3_progression_mod/progression.json.
// Wrapped in an IIFE so this file's only top-level name is BLOCKED_BLOCKS_LV_STAGE_ID -
// other server_scripts files load the same progression.json independently and must not
// collide on shared top-level names (KubeJS loads all server_scripts into one scope).
//
// KubeJS's own class filter denies java.nio/java.io entirely (scripts can't read files
// directly), so the raw bytes come from ProgressionTiers.rawJson() (our own mod class,
// unrestricted) instead - this script still does its own JSON.parse of that text.
const BLOCKED_BLOCKS_LV_STAGE_ID = (() => {
    const ProgressionTiers = Java.loadClass('com.civtfg.progression.stage.ProgressionTiers')
    const config = JSON.parse(String(ProgressionTiers.rawJson()))
    return config.tiers.find(t => t.key === 'LV').stageId
})()

BlockEvents.rightClicked('minecraft:furnace', event => {
  if (!event.player.stages.has(BLOCKED_BLOCKS_LV_STAGE_ID)) {
    event.player.tell("You don't know how to use this yet.")
    event.cancel()
  }
})

// One gating (multi-)block per age transition - each locked until the team has reached
// the tier listed as "requiresTier" in progression.json's "gates" array. This only
// handles the "interaction" and "placement" mechanisms (plus "gtceu_voltage_interaction"
// further down, a variant of "interaction" for GTCEU machines); the "possession" mechanism
// is enforced Java-side instead, by GatedItemEnforcer scanning inventories - placement/
// interaction gates alone can be bypassed once a player has some means of placing/acquiring
// an item other than the exact action being listened for here.
// Rhino (KubeJS's script engine here) doesn't support object-spread in object literals -
// mutate each parsed gate in place with its resolved stageId instead of spreading it into
// a new object.
const BLOCKED_BLOCKS_GATES = (() => {
    const ProgressionTiers = Java.loadClass('com.civtfg.progression.stage.ProgressionTiers')
    const config = JSON.parse(String(ProgressionTiers.rawJson()))
    return (config.gates || []).map(gate => {
        gate.stageId = config.tiers.find(t => t.key === gate.requiresTier).stageId
        return gate
    })
})()

BLOCKED_BLOCKS_GATES.forEach(gate => {
    if (gate.mechanism === 'interaction' && !gate.entity) {
        gate.blocks.forEach(id => BlockEvents.rightClicked(id, event => {
            if (!event.player.stages.has(gate.stageId)) {
                event.player.tell(gate.message)
                event.cancel()
            }
        }))
    } else if (gate.mechanism === 'placement') {
        // BlockEvents.placed only fires *after* the block is already set into the world -
        // KubeJS/Architectury build it on Forge's EntityPlaceEvent, whose "cancel" is a
        // revert (set the position back to what it was), not a true pre-placement veto.
        // On singleplayer (client+server, no network round trip) that place-then-revert
        // is imperceptible; on a real dedicated server the extra round trip (place -> event
        // -> revert -> correction packet back to the client) is visibly slow (reported:
        // several seconds). Kept as-is below since it's still needed as an
        // automation-proof backstop (a machine placing the block bypasses any check that
        // only runs on a player's own right-click). The fast path that avoids ever setting
        // the block in the first place is Java-side, PlacementGateEnforcer.
        gate.blocks.forEach(id => BlockEvents.placed(id, event => {
            // event.player is null when a non-player entity placed it (EntityEventJS#getPlayer)
            if (event.player && !event.player.stages.has(gate.stageId)) {
                event.player.tell(gate.message)
                event.cancel()
            }
        }))
    } else if (gate.mechanism === 'interaction' && gate.entity) {
        // Rockets are entities (like boats/minecarts), not blocks - there's no
        // 'EntityEvents.rightClicked' in KubeJS, and unlike BlockEvents.rightClicked,
        // ItemEvents.entityInteracted has no id-filter argument, so the target entity's
        // type has to be checked manually inside the callback instead.
        ItemEvents.entityInteracted(event => {
            if (gate.blocks.indexOf(event.target.type) === -1) {
                return
            }
            if (!event.player.stages.has(gate.stageId)) {
                event.player.tell(gate.message)
                event.cancel()
            }
        })
    }
})

// GTCEU machine voltage lookup shared by all three "gtceu_voltage_*" gates below. GTCEU has
// no block tag for "every machine of tier X", but every GTCEU machine block (hatches/buses
// included) is a MetaMachineBlock whose MachineDefinition#getTier() indexes GTValues.VN
// (LV=1, MV=2, HV=3, EV=4, IV=5 - confirmed via javap against the actual gtceu jar). Which
// blocks this actually matches can be listed in-game with tools/dump_gated_machines.js.
// Wrapped in an IIFE so the Java.loadClass results live in function scope (Pitfall #20).
const BLOCKED_BLOCKS_VOLTAGE_OF = (() => {
    const MetaMachineBlock = Java.loadClass('com.gregtechceu.gtceu.api.block.MetaMachineBlock')
    const GTValues = Java.loadClass('com.gregtechceu.gtceu.api.GTValues')
    // instanceof, not MetaMachineBlock.isInstance(...) - Java.loadClass returns a Rhino class
    // wrapper exposing only the class's own static members (Pitfall #25). String(...): VN
    // holds Java strings, compared with === against progression.json's JS strings below.
    return block => (block instanceof MetaMachineBlock) ? String(GTValues.VN[block.getDefinition().getTier()]) : null
})()

// @return the first of {@code gates} that locks {@code block} (a GTCEU machine of that
// gate's voltage, not listed in its exceptBlocks) for {@code player}, or null.
function blockedBlocksLockedVoltageGate(gates, block, blockId, player) {
    var voltage = BLOCKED_BLOCKS_VOLTAGE_OF(block)
    if (voltage === null) return null
    for (var i = 0; i < gates.length; i++) {
        var gate = gates[i]
        var excepted = gate.exceptBlocks && gate.exceptBlocks.indexOf(blockId) !== -1
        if (gate.voltage === voltage && !excepted && !player.stages.has(gate.stageId)) {
            return gate
        }
    }
    return null
}

const BLOCKED_BLOCKS_VOLTAGE_PLACEMENT_GATES = BLOCKED_BLOCKS_GATES.filter(gate => gate.mechanism === 'gtceu_voltage_placement')
const BLOCKED_BLOCKS_VOLTAGE_INTERACTION_GATES = BLOCKED_BLOCKS_GATES.filter(gate => gate.mechanism === 'gtceu_voltage_interaction')

// The fast path for "placement"/"gtceu_voltage_placement" (refusing the right-click before
// anything is placed) lives in Java, PlacementGateEnforcer: it has to run on the client too,
// otherwise the client predicts the placement and the item looks gone (it used to be here).
// This script only keeps the BlockEvents.placed backstops for placements that don't come
// from a player's right-click.

// Backstop for "gtceu_voltage_placement", same reasoning as the "placement" branch above
// (catches placements that didn't go through a player's own right-click). No id filter, so
// it runs on every block placement - blockedBlocksLockedVoltageGate returns immediately for
// anything that isn't a GTCEU machine.
if (BLOCKED_BLOCKS_VOLTAGE_PLACEMENT_GATES.length > 0) {
    BlockEvents.placed(event => {
        // null when a non-player entity placed it (EntityEventJS#getPlayer)
        if (!event.player) {
            return
        }
        const gate = blockedBlocksLockedVoltageGate(BLOCKED_BLOCKS_VOLTAGE_PLACEMENT_GATES, event.block.blockState.getBlock(), String(event.block.id), event.player)
        if (gate) {
            event.player.tell(gate.message)
            event.cancel()
        }
    })
}

// "gtceu_voltage_interaction": right-clicking a GTCEU machine of a gated voltage. Only a
// plain player right-click is gated here, on purpose - no dispenser/fire-starter-style edge
// cases to worry about for these, unlike the Bloomery/Blast Furnace gates above.
if (BLOCKED_BLOCKS_VOLTAGE_INTERACTION_GATES.length > 0) {
    BlockEvents.rightClicked(event => {
        const gate = blockedBlocksLockedVoltageGate(BLOCKED_BLOCKS_VOLTAGE_INTERACTION_GATES, event.block.blockState.getBlock(), String(event.block.id), event.player)
        if (gate) {
            event.player.tell(gate.message)
            event.cancel()
        }
    })
}
