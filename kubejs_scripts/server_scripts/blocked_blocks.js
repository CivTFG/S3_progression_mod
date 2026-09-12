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
        // only runs on a player's own right-click), but see the pre-emptive check further
        // down for the fast path that avoids ever setting the block in the first place.
        gate.blocks.forEach(id => BlockEvents.placed(id, event => {
            if (!event.player.stages.has(gate.stageId)) {
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

// Fast pre-emptive path for "placement" gates: cancelling a right-click on ANY block while
// holding a gated block item, before the placement can ever happen, so there's no
// place-then-revert round trip to be slow on a dedicated server (see the comment on the
// "placement" branch above). This intentionally checks the held item rather than a specific
// target block, since we don't know in advance which position the new block would land on -
// the tradeoff is that right-clicking some OTHER block (e.g. a chest) while merely holding a
// gated item in hand also gets cancelled, even though nothing would have been placed. That's
// judged an acceptable rare inconvenience (switch hands or empty your hand and try again)
// against the confirmed, worse alternative of multi-second server lag on every real
// placement attempt. Registered with no id filter (BlockEvents.rightClicked supports that -
// it's an "extra", not a required argument) since it must fire for every block the player
// might be placing against, not just one specific target block.
const BLOCKED_BLOCKS_PLACEMENT_GATES = BLOCKED_BLOCKS_GATES.filter(gate => gate.mechanism === 'placement')

if (BLOCKED_BLOCKS_PLACEMENT_GATES.length > 0) {
    BlockEvents.rightClicked(event => {
        const heldItemId = event.item.id
        for (let i = 0; i < BLOCKED_BLOCKS_PLACEMENT_GATES.length; i++) {
            const gate = BLOCKED_BLOCKS_PLACEMENT_GATES[i]
            if (gate.blocks.indexOf(heldItemId) !== -1 && !event.player.stages.has(gate.stageId)) {
                event.player.tell(gate.message)
                event.cancel()
                return
            }
        }
    })
}

// "gtceu_voltage_interaction" gates an entire GTCEU voltage tier's worth of machines at once
// (e.g. "all LV machines") instead of listing individual block ids - GTCEU has no block tag
// for "every machine of tier X" (only item tags like #gtceu:circuits/mv exist), but every
// GTCEU machine block (including hatches/buses/casings - deliberately "all", not just the
// simple single-block machines) is an instance of MetaMachineBlock, whose
// MachineDefinition#getTier() gives the same voltage-tier index GTValues.VN is keyed by
// (LV=1, MV=2, HV=3, EV=4, IV=5 - confirmed via javap against the actual gtceu jar). Only a
// plain player right-click is gated here, on purpose - no dispenser/fire-starter-style edge
// cases to worry about for these, unlike the Bloomery/Blast Furnace gates above.
const BLOCKED_BLOCKS_GTCEU_VOLTAGE_GATES = BLOCKED_BLOCKS_GATES.filter(gate => gate.mechanism === 'gtceu_voltage_interaction')

if (BLOCKED_BLOCKS_GTCEU_VOLTAGE_GATES.length > 0) {
    const MetaMachineBlock = Java.loadClass('com.gregtechceu.gtceu.api.block.MetaMachineBlock')
    const GTValues = Java.loadClass('com.gregtechceu.gtceu.api.GTValues')

    BlockEvents.rightClicked(event => {
        const block = event.block.blockState.getBlock()
        if (!MetaMachineBlock.isInstance(block)) {
            return
        }
        const voltage = GTValues.VN[block.getDefinition().getTier()]
        for (let i = 0; i < BLOCKED_BLOCKS_GTCEU_VOLTAGE_GATES.length; i++) {
            const gate = BLOCKED_BLOCKS_GTCEU_VOLTAGE_GATES[i]
            if (gate.voltage === voltage && !event.player.stages.has(gate.stageId)) {
                event.player.tell(gate.message)
                event.cancel()
                return
            }
        }
    })
}