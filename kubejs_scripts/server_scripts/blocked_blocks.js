// Furnace is gated behind the LV tier's stage - read from the shared progression
// config rather than hardcoding the stage id, see config_files/s3_progression_mod/progression.json.
// Wrapped in an IIFE so this file's only top-level name is BLOCKED_BLOCKS_LV_STAGE_ID -
// other server_scripts files load the same progression.json independently and must not
// collide on shared top-level names (KubeJS loads all server_scripts into one scope).
const BLOCKED_BLOCKS_LV_STAGE_ID = (() => {
    const Files = Java.loadClass('java.nio.file.Files')
    const Paths = Java.loadClass('java.nio.file.Paths')
    const path = Paths.get('config', 's3_progression_mod', 'progression.json')
    const config = JSON.parse(String(Files.readString(path)))
    return config.tiers.find(t => t.key === 'LV').stageId
})()

BlockEvents.rightClicked('minecraft:furnace', event => {
  if (!event.player.stages.has(BLOCKED_BLOCKS_LV_STAGE_ID)) {
    event.player.tell("You don't know how to use this yet.")
    event.cancel()
  }
})