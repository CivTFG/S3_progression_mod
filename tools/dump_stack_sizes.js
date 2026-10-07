// One-off diagnostic, NOT shipped with the mod: the real max stack size of every ingredient
// of the science recipes, read from the running game (TFC assigns stack sizes at runtime from
// its item size/weight definitions, so they can't be read from the files), plus the slots each
// machine recipe needs.
//
// Usage: copy into an instance's kubejs/server_scripts/ (next to s3_progression_mod/, which
// must contain science_recipes.js - this reads its SCIENCE_RECIPES, KubeJS server scripts share
// one global scope), run /reload, then (op only):
//   /progression_dump_stacks      science-recipe ingredients + slot check per recipe
//                                 -> kubejs/exported/s3_stack_sizes.json, problems in chat/log
//   /progression_dump_stacks all  every registered item's stack size
//                                 -> kubejs/exported/s3_stack_sizes_all.json
// Log lines are prefixed "[s3_stack_dump]". Delete the script again afterwards.
//
// Slot rules: primitive_assembler and GTCEU machines share 9 item slots, each holding one stack
// of one item, so an input needs ceil(count / stackSize) slots. For a "#tag" input the item
// with the SMALLEST stack size is assumed (worst case). Crafting-table recipes take one item
// per grid slot, so stack sizes don't matter there.
//
// var + function scope only, prefixed names - see Pitfall #20 and the shared-scope note in
// progression_commands.js.

ServerEvents.commandRegistry(event => {
    var Commands = event.commands
    event.register(
        Commands.literal('progression_dump_stacks')
            .requires(src => src.hasPermission(2))
            .executes(ctx => s3RunStackDump(ctx, s3DumpStackSizes))
            .then(Commands.literal('all').executes(ctx => s3RunStackDump(ctx, s3DumpAllStackSizes)))
    )
})

function s3RunStackDump(ctx, dump) {
    try {
        return dump(ctx)
    } catch (e) {
        console.error(`[s3_stack_dump] FAILED: ${e}`)
        ctx.source.sendSystemMessage(Component.red(`Stack size dump failed: ${e}`))
        return 0
    }
}

// Every registered item (Ingredient.all = every item in the registry), grouped output plus a
// per-stack-size count summary.
function s3DumpAllStackSizes(ctx) {
    var s3AllStacks = {}
    var s3Counts = {}
    Ingredient.all.getItemIds().forEach(id => {
        var s3Id = String(id)
        var s3Size = Item.of(s3Id).getMaxStackSize()
        s3AllStacks[s3Id] = s3Size
        s3Counts[s3Size] = (s3Counts[s3Size] || 0) + 1
    })
    JsonIO.write('kubejs/exported/s3_stack_sizes_all.json', JsonIO.parseRaw(JSON.stringify({ countsByStackSize: s3Counts, stackSizes: s3AllStacks })))
    var s3Summary = Object.keys(s3Counts).map(size => `${s3Counts[size]}x stack ${size}`).join(', ')
    console.info(`[s3_stack_dump] all items: ${s3Summary}`)
    ctx.source.sendSystemMessage(Component.literal(`${Object.keys(s3AllStacks).length} items written to kubejs/exported/s3_stack_sizes_all.json (${s3Summary})`))
    return 1
}

function s3DumpStackSizes(ctx) {
    var s3Tell = text => {
        console.info(`[s3_stack_dump] ${text}`)
        ctx.source.sendSystemMessage(Component.literal(text))
    }
    if (typeof SCIENCE_RECIPES === 'undefined') {
        s3Tell('SCIENCE_RECIPES not found - is science_recipes.js loaded?')
        return 0
    }

    var s3Stacks = {}          // item id -> max stack size
    var s3TagMembers = {}      // "#tag" -> [item ids]

    var s3StackOf = id => {
        if (s3Stacks[id] === undefined) {
            var s3Stack = Item.of(id)
            s3Stacks[id] = s3Stack.isEmpty() ? -1 : s3Stack.getMaxStackSize()
        }
        return s3Stacks[id]
    }

    // smallest stack size among the items an input accepts (-1 = unknown item / empty tag)
    var s3InputStack = item => {
        var s3Ids
        if (String(item).charAt(0) === '#') {
            if (!s3TagMembers[item]) {
                s3TagMembers[item] = []
                Ingredient.of(item).getItemIds().forEach(id => s3TagMembers[item].push(String(id)))
            }
            s3Ids = s3TagMembers[item]
        } else {
            s3Ids = [String(item)]
        }
        var s3Min = -1
        s3Ids.forEach(id => {
            var s3Size = s3StackOf(id)
            if (s3Size > 0 && (s3Min < 0 || s3Size < s3Min)) s3Min = s3Size
        })
        return s3Min
    }

    var s3Report = []
    var s3Problems = 0
    SCIENCE_RECIPES.forEach((recipe, index) => {
        var s3Entry = { index: index, age: recipe.age, category: recipe.category, machine: recipe.machine, inputs: [] }
        var s3Slots = 0
        var s3Unknown = false
        recipe.inputs.forEach(input => {
            var s3Size = s3InputStack(input.item)
            var s3Need = s3Size > 0 ? Math.ceil(input.count / s3Size) : 1
            if (s3Size <= 0) s3Unknown = true
            s3Slots += s3Need
            s3Entry.inputs.push({ item: input.item, count: input.count, stackSize: s3Size, slots: s3Need })
        })
        s3Entry.slotsNeeded = s3Slots
        s3Entry.fits = recipe.machine === 'crafting_table' || s3Slots <= 9
        s3Report.push(s3Entry)
        if (s3Unknown) {
            s3Problems++
            s3Tell(`#${index} ${recipe.age} ${recipe.category}: contains an unknown item or empty tag`)
        }
        if (!s3Entry.fits) {
            s3Problems++
            var s3Detail = s3Entry.inputs.filter(i => i.slots > 1).map(i => `${i.count}x ${i.item} (stack ${i.stackSize})`).join(', ')
            s3Tell(`#${index} ${recipe.age} ${recipe.category} (${recipe.machine}): needs ${s3Slots} of 9 slots - ${s3Detail}`)
        }
    })

    var s3Json = JSON.stringify({ stackSizes: s3Stacks, tags: s3TagMembers, recipes: s3Report })
    JsonIO.write('kubejs/exported/s3_stack_sizes.json', JsonIO.parseRaw(s3Json))
    s3Tell(`${SCIENCE_RECIPES.length} recipes, ${Object.keys(s3Stacks).length} items checked, ${s3Problems} problem(s). Written to kubejs/exported/s3_stack_sizes.json`)
    return 1
}
