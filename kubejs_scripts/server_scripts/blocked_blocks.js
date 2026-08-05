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
// handles the "interaction" and "placement" mechanisms; the "possession" mechanism (LV
// generators) is enforced Java-side instead, by GatedItemEnforcer scanning inventories -
// placement/interaction gates alone can be bypassed once a player has some means of
// placing/acquiring an item other than the exact action being listened for here.
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
        gate.blocks.forEach(id => BlockEvents.placed(id, event => {
            if (!event.player.stages.has(gate.stageId)) {
                event.player.tell(gate.message)
                event.cancel()
            }
        }))
    } else if (gate.mechanism === 'interaction' && gate.entity) {
        gate.blocks.forEach(id => EntityEvents.rightClicked(id, event => {
            if (!event.player.stages.has(gate.stageId)) {
                event.player.tell(gate.message)
                event.cancel()
            }
        }))
    }
})