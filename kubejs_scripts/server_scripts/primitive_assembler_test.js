// Test recipe for the unpowered Primitive Assembler (s3_progression_mod:primitive_assembler).
ServerEvents.recipes(event => {
  event.custom({
    type: 's3_progression_mod:primitive_assembler',
    inputs: [
      { ingredient: { item: 'minecraft:cobblestone' }, count: 16 },
      { ingredient: { tag: 'minecraft:logs' }, count: 16 }
    ],
    fluid_input: { fluid: 'minecraft:water', amount: 1000 },
    output: { item: 'tfc:rock/loose/marble', count: 1 },
    duration: 100
  }).id('s3_progression_mod:test_marble_pebble');
});
