package com.pvpkits.commands

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.TextUtils
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class KitCommand(private val plugin: PvPKitsPlugin) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(TextUtils.parse("<red>Only players can use this command!"))
            return true
        }

        when (command.name.lowercase()) {
            "kit" -> handleKitCommand(sender, args)
            "createkit" -> handleCreateKit(sender, args)
            "deletekit" -> handleDeleteKit(sender, args)
        }

        return true
    }

    private fun handleKitCommand(player: Player, args: Array<out String>) {
        if (!player.hasPermission("pvpkits.use")) {
            player.sendMessage(getMessage("no-permission"))
            return
        }

        if (args.isEmpty()) {
            plugin.kitGUI.openKitMenu(player)
            return
        }

        val kitName = args[0]
        val kit = plugin.kitManager.getKit(kitName)

        if (kit == null) {
            player.sendMessage(getMessage("kit-not-found"))
            return
        }

        if (kit.permission != null && !player.hasPermission(kit.permission)) {
            player.sendMessage(getMessage("no-permission"))
            return
        }

        if (plugin.kitManager.hasCooldown(player.uniqueId, kitName)) {
            val remaining = plugin.kitManager.getCooldownRemaining(player.uniqueId, kitName)
            val message = getMessage("kit-cooldown")
                .replaceText { it.matchLiteral("{time}").replacement(TextUtils.formatTime(remaining)) }
            player.sendMessage(message)
            return
        }

        if (plugin.kitManager.giveKit(player, kitName)) {
            val message = getMessage("kit-received")
                .replaceText { it.matchLiteral("{kit}").replacement(TextUtils.parseAuto(kit.displayName)) }
            player.sendMessage(message)
        }
    }

    private fun handleCreateKit(player: Player, args: Array<out String>) {
        if (!player.hasPermission("pvpkits.admin")) {
            player.sendMessage(getMessage("no-permission"))
            return
        }

        if (args.isEmpty()) {
            player.sendMessage(TextUtils.parse("<red>Usage: /createkit <name>"))
            return
        }

        val kitName = args[0]
        val items = player.inventory.contents.filterNotNull().filter { it.type != Material.AIR }

        if (items.isEmpty()) {
            player.sendMessage(TextUtils.parse("<red>Your inventory is empty!"))
            return
        }

        plugin.kitManager.createKit(
            name = kitName,
            displayName = "<yellow>$kitName",
            icon = null,
            permission = "pvpkits.kit.$kitName",
            cooldown = 60,
            description = listOf("<gray>Custom kit created by ${player.name}"),
            items = items
        )

        val message = getMessage("kit-created")
            .replaceText { it.matchLiteral("{kit}").replacement(kitName) }
        player.sendMessage(message)
    }

    private fun handleDeleteKit(player: Player, args: Array<out String>) {
        if (!player.hasPermission("pvpkits.admin")) {
            player.sendMessage(getMessage("no-permission"))
            return
        }

        if (args.isEmpty()) {
            player.sendMessage(TextUtils.parse("<red>Usage: /deletekit <name>"))
            return
        }

        val kitName = args[0]
        if (plugin.kitManager.deleteKit(kitName)) {
            val message = getMessage("kit-deleted")
                .replaceText { it.matchLiteral("{kit}").replacement(kitName) }
            player.sendMessage(message)
        } else {
            player.sendMessage(getMessage("kit-not-found"))
        }
    }

    private fun getMessage(key: String): net.kyori.adventure.text.Component {
        val prefix = plugin.config.getString("messages.prefix") ?: ""
        val message = plugin.config.getString("messages.$key") ?: key
        return TextUtils.parseAuto(prefix + message)
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        if (command.name.lowercase() == "kit" && args.size == 1) {
            return plugin.kitManager.getAllKits()
                .map { it.name }
                .filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return null
    }
}
