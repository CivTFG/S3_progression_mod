BlockEvents.rightClicked('minecraft:furnace', event => {
  if (!event.player.stages.has('furnace_unlocked')) {
    event.player.tell("You don't know how to use this yet.")
    event.cancel()
  }
})