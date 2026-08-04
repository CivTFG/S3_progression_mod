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