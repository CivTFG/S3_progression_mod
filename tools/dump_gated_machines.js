// One-off diagnostic, NOT shipped with the mod: lists every block the
// "gtceu_voltage_interaction"/"gtceu_voltage_crafting" gates actually match, using the exact
// same runtime check as blocked_blocks.js / CraftingGateEnforcer (MetaMachineBlock +
// getDefinition().getTier() -> GTValues.VN) instead of guessing from id prefixes.
//
// Usage: copy into an instance's kubejs/server_scripts/, run /kubejs reload_server, then
// /progression_dump_machines (op only). Output goes to logs/kubejs/server.log, one line per
// machine, prefixed "[s3_gate_dump]". Delete the script again afterwards.
//
// Everything lives inside functions and uses var (not const/let in bare blocks) - see
// Pitfall #20 in CLAUDE.md.

ServerEvents.commandRegistry(event => {
    var Commands = event.commands
    event.register(
        Commands.literal('progression_dump_machines')
            .requires(src => src.hasPermission(2))
            .executes(ctx => {
                try {
                    return s3DumpGatedMachines(ctx)
                } catch (e) {
                    console.error(`[s3_gate_dump] FAILED: ${e}`)
                    if (e.javaException) {
                        console.error(e.javaException)
                    }
                    if (ctx.source.entity) {
                        ctx.source.entity.tell(`Dump failed: ${e}`)
                    }
                    return 0
                }
            })
    )
})

function s3DumpGatedMachines(ctx) {
    var BuiltInRegistries = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries')
    var MetaMachineBlock = Java.loadClass('com.gregtechceu.gtceu.api.block.MetaMachineBlock')
    var GTValues = Java.loadClass('com.gregtechceu.gtceu.api.GTValues')
    var ProgressionTiers = Java.loadClass('com.civtfg.progression.stage.ProgressionTiers')

    var config = JSON.parse(String(ProgressionTiers.rawJson()))
    var gatedVoltages = {}
    config.gates.forEach(gate => {
        if (gate.voltage) {
            gatedVoltages[gate.voltage] = gate.requiresTier
        }
    })

    var byVoltage = {}
    BuiltInRegistries.BLOCK.entrySet().forEach(entry => {
        var block = entry.getValue()
        if (!(block instanceof MetaMachineBlock)) {
            return
        }
        var voltage = String(GTValues.VN[block.getDefinition().getTier()])
        if (!gatedVoltages[voltage]) {
            return
        }
        if (!byVoltage[voltage]) {
            byVoltage[voltage] = []
        }
        byVoltage[voltage].push(String(entry.getKey().location()))
    })

    var summary = []
    Object.keys(gatedVoltages).forEach(voltage => {
        var ids = (byVoltage[voltage] || []).sort()
        ids.forEach(id => console.info(`[s3_gate_dump] ${voltage} ${gatedVoltages[voltage]} ${id}`))
        summary.push(`${voltage}: ${ids.length}`)
    })
    console.info(`[s3_gate_dump] DONE ${summary.join(', ')}`)
    var sender = ctx.source.entity
    if (sender) {
        sender.tell(`Dumped gated machines (${summary.join(', ')}) to logs/kubejs/server.log`)
    }
    return 1
}
