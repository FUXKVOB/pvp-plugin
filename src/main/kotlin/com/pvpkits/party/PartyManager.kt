package com.pvpkits.party

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.TextUtils
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Duel Challenge System - вызовы на дуэль между игроками
 * 
 * Заменяет старую Party систему на более подходящую для PvP
 */
class PartyManager(private val plugin: PvPKitsPlugin) {
    
    private val challenges = ConcurrentHashMap<UUID, DuelChallenge>() // challenger -> challenge
    private val pendingChallenges = ConcurrentHashMap<UUID, MutableList<DuelChallenge>>() // target -> challenges
    
    // Party system (legacy - for group challenges)
    private val parties = ConcurrentHashMap<UUID, Party>() // party ID -> party
    private val playerParties = ConcurrentHashMap<UUID, UUID>() // player UUID -> party ID
    
    /**
     * Challenge player to duel
     */
    fun challengePlayer(challenger: Player, target: Player, kitName: String): Boolean {
        // Validation
        if (challenger.uniqueId == target.uniqueId) {
            challenger.sendMessage(TextUtils.format("<red>You cannot challenge yourself!"))
            return false
        }
        
        if (challenges.containsKey(challenger.uniqueId)) {
            challenger.sendMessage(TextUtils.format("<red>You already have a pending challenge!"))
            return false
        }
        
        if (plugin.duelManager.isInMatch(challenger.uniqueId) || plugin.duelManager.isInMatch(target.uniqueId)) {
            challenger.sendMessage(TextUtils.format("<red>One of you is already in a match!"))
            return false
        }
        
        if (plugin.duelManager.isInQueue(challenger.uniqueId) || plugin.duelManager.isInQueue(target.uniqueId)) {
            challenger.sendMessage(TextUtils.format("<red>One of you is already in queue!"))
            return false
        }
        
        // Check if kit exists
        if (plugin.kitManager.getKit(kitName) == null) {
            challenger.sendMessage(TextUtils.format("<red>Kit not found: $kitName"))
            return false
        }
        
        // Create challenge
        val challenge = DuelChallenge(
            challenger = challenger.uniqueId,
            target = target.uniqueId,
            challengerName = challenger.name,
            targetName = target.name,
            kitName = kitName,
            createdAt = System.currentTimeMillis()
        )
        
        challenges[challenger.uniqueId] = challenge
        pendingChallenges.getOrPut(target.uniqueId) { mutableListOf() }.add(challenge)
        
        // Notify both players
        challenger.sendMessage("")
        challenger.sendMessage(TextUtils.format("<gradient:#ffd700:#ffaa00><bold>═══ DUEL CHALLENGE ═══</bold></gradient>"))
        challenger.sendMessage(TextUtils.format("<green>✓ Challenge sent to <yellow>${target.name}"))
        challenger.sendMessage(TextUtils.format("<gray>Kit: <white>$kitName"))
        challenger.sendMessage(TextUtils.format("<gray>Waiting for response..."))
        challenger.sendMessage(TextUtils.format("<gradient:#ffd700:#ffaa00><bold>═══════════════════════</bold></gradient>"))
        challenger.sendMessage("")
        
        target.sendMessage("")
        target.sendMessage(TextUtils.format("<gradient:#ff0000:#ff6b6b><bold>═══ DUEL CHALLENGE ═══</bold></gradient>"))
        target.sendMessage(TextUtils.format("<yellow>${challenger.name}</yellow> <gray>challenged you to a duel!"))
        target.sendMessage(TextUtils.format("<gray>Kit: <white>$kitName"))
        target.sendMessage("")
        target.sendMessage(TextUtils.format("<green>/duel accept ${challenger.name}</green> <gray>- Accept"))
        target.sendMessage(TextUtils.format("<red>/duel deny ${challenger.name}</red> <gray>- Deny"))
        target.sendMessage(TextUtils.format("<gray>Expires in 60 seconds"))
        target.sendMessage(TextUtils.format("<gradient:#ff0000:#ff6b6b><bold>═══════════════════════</bold></gradient>"))
        target.sendMessage("")
        
        // Play sound
        target.playSound(target.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f)
        
        return true
    }
    
    /**
     * Accept duel challenge
     */
    fun acceptChallenge(target: Player, challengerName: String): Boolean {
        val targetChallenges = pendingChallenges[target.uniqueId] ?: run {
            target.sendMessage(TextUtils.format("<red>You have no pending challenges!"))
            return false
        }
        
        val challenge = targetChallenges.find { 
            it.challengerName.equals(challengerName, ignoreCase = true) 
        } ?: run {
            target.sendMessage(TextUtils.format("<red>No challenge from $challengerName!"))
            return false
        }
        
        // Check if expired (60 seconds)
        if (System.currentTimeMillis() - challenge.createdAt > 60000) {
            targetChallenges.remove(challenge)
            challenges.remove(challenge.challenger)
            target.sendMessage(TextUtils.format("<red>Challenge expired!"))
            return false
        }
        
        val challenger = plugin.server.getPlayer(challenge.challenger) ?: run {
            targetChallenges.remove(challenge)
            challenges.remove(challenge.challenger)
            target.sendMessage(TextUtils.format("<red>Challenger is offline!"))
            return false
        }
        
        // Remove challenge
        targetChallenges.remove(challenge)
        challenges.remove(challenge.challenger)
        
        // Start duel
        target.sendMessage(TextUtils.format("<green>✓ Challenge accepted!"))
        challenger.sendMessage(TextUtils.format("<green>✓ <yellow>${target.name}</yellow> accepted your challenge!"))
        
        // Start match using DuelManager
        plugin.duelManager.startDirectDuel(challenger, target, challenge.kitName)
        
        return true
    }
    
    /**
     * Deny duel challenge
     */
    fun denyChallenge(target: Player, challengerName: String): Boolean {
        val targetChallenges = pendingChallenges[target.uniqueId] ?: run {
            target.sendMessage(TextUtils.format("<red>You have no pending challenges!"))
            return false
        }
        
        val challenge = targetChallenges.find { 
            it.challengerName.equals(challengerName, ignoreCase = true) 
        } ?: run {
            target.sendMessage(TextUtils.format("<red>No challenge from $challengerName!"))
            return false
        }
        
        // Remove challenge
        targetChallenges.remove(challenge)
        challenges.remove(challenge.challenger)
        
        // Notify both
        target.sendMessage(TextUtils.format("<yellow>Challenge denied"))
        
        val challenger = plugin.server.getPlayer(challenge.challenger)
        challenger?.sendMessage(TextUtils.format("<red><yellow>${target.name}</yellow> denied your challenge"))
        
        return true
    }
    
    /**
     * Cancel own challenge
     */
    fun cancelChallenge(challenger: Player): Boolean {
        val challenge = challenges.remove(challenger.uniqueId) ?: run {
            challenger.sendMessage(TextUtils.format("<red>You have no pending challenge!"))
            return false
        }
        
        // Remove from target's pending
        pendingChallenges[challenge.target]?.remove(challenge)
        
        challenger.sendMessage(TextUtils.format("<yellow>Challenge cancelled"))
        
        val target = plugin.server.getPlayer(challenge.target)
        target?.sendMessage(TextUtils.format("<gray><yellow>${challenger.name}</yellow> cancelled their challenge"))
        
        return true
    }
    
    /**
     * Get pending challenges for player
     */
    fun getPendingChallenges(player: Player): List<DuelChallenge> {
        return pendingChallenges[player.uniqueId]?.toList() ?: emptyList()
    }
    
    /**
     * Get player's active challenge
     */
    fun getActiveChallenge(player: Player): DuelChallenge? {
        return challenges[player.uniqueId]
    }
    
    /**
     * Check if player has pending challenge
     */
    fun hasPendingChallenge(player: Player): Boolean {
        return challenges.containsKey(player.uniqueId)
    }
    
    /**
     * Cleanup expired challenges
     */
    fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val expired = mutableListOf<DuelChallenge>()
        
        challenges.values.forEach { challenge ->
            if (now - challenge.createdAt > 60000) {
                expired.add(challenge)
            }
        }
        
        expired.forEach { challenge ->
            challenges.remove(challenge.challenger)
            pendingChallenges[challenge.target]?.remove(challenge)
            
            // Notify
            plugin.server.getPlayer(challenge.challenger)?.sendMessage(
                TextUtils.format("<gray>Your challenge to <yellow>${challenge.targetName}</yellow> expired")
            )
        }
    }
    
    /**
     * Cleanup player data (challenges and party)
     */
    fun cleanupPlayer(uuid: UUID) {
        // Remove challenge data
        val challenge = challenges.remove(uuid)
        if (challenge != null) {
            pendingChallenges[challenge.target]?.remove(challenge)
        }
        pendingChallenges.remove(uuid)
        
        // Remove party data
        val party = getPlayerParty(uuid)
        if (party != null) {
            if (party.leader == uuid) {
                disbandParty(party)
            } else {
                party.members.remove(uuid)
                playerParties.remove(uuid)
                broadcastToParty(party, "<yellow>${plugin.server.getOfflinePlayer(uuid).name}</yellow> <gray>left (disconnected)")
            }
        }
    }
    
    /**
     * Get memory stats for challenges
     */
    fun getChallengeStats(): Map<String, Any> {
        return mapOf(
            "active_challenges" to challenges.size,
            "total_pending" to pendingChallenges.values.sumOf { it.size }
        )
    }
    
    /**
     * Create a new party
     */
    fun createParty(leader: Player): Party {
        val partyId = UUID.randomUUID()
        val party = Party(
            id = partyId,
            leader = leader.uniqueId,
            members = mutableSetOf(leader.uniqueId)
        )
        
        parties[partyId] = party
        playerParties[leader.uniqueId] = partyId
        
        leader.sendMessage(TextUtils.format("<green>✓ Party created! You are the leader."))
        
        return party
    }
    
    /**
     * Invite player to party
     */
    fun invitePlayer(party: Party, inviter: Player, target: Player): Boolean {
        if (party.leader != inviter.uniqueId) {
            inviter.sendMessage(TextUtils.format("<red>Only the party leader can invite players!"))
            return false
        }
        
        if (playerParties.containsKey(target.uniqueId)) {
            inviter.sendMessage(TextUtils.format("<red>${target.name} is already in a party!"))
            return false
        }
        
        if (party.members.size >= party.maxSize) {
            inviter.sendMessage(TextUtils.format("<red>Party is full! (${party.maxSize} max)"))
            return false
        }
        
        party.invites[target.uniqueId] = System.currentTimeMillis()
        
        target.sendMessage("")
        target.sendMessage(TextUtils.format("<gradient:#00ff00:#00aa00><bold>═══ PARTY INVITE ═══</bold></gradient>"))
        target.sendMessage(TextUtils.format("<yellow>${inviter.name}</yellow> <gray>invited you to their party!"))
        target.sendMessage(TextUtils.format("<green>/party accept</green> <gray>or</gray> <red>/party deny"))
        target.sendMessage(TextUtils.format("<gray>Expires in 60 seconds"))
        target.sendMessage(TextUtils.format("<gradient:#00ff00:#00aa00><bold>═══════════════════</bold></gradient>"))
        target.sendMessage("")
        
        inviter.sendMessage(TextUtils.format("<green>✓ Invited <yellow>${target.name}</yellow> to the party"))
        
        return true
    }
    
    /**
     * Accept party invite
     */
    fun acceptInvite(player: Player): Boolean {
        val party = parties.values.find { it.invites.containsKey(player.uniqueId) } ?: run {
            player.sendMessage(TextUtils.format("<red>You have no pending party invites!"))
            return false
        }
        
        // Check if invite expired (60 seconds)
        val inviteTime = party.invites[player.uniqueId] ?: return false
        if (System.currentTimeMillis() - inviteTime > 60000) {
            party.invites.remove(player.uniqueId)
            player.sendMessage(TextUtils.format("<red>Party invite expired!"))
            return false
        }
        
        party.invites.remove(player.uniqueId)
        party.members.add(player.uniqueId)
        playerParties[player.uniqueId] = party.id
        
        // Notify all party members
        broadcastToParty(party, "<green>✓ <yellow>${player.name}</yellow> <green>joined the party! <gray>(${party.members.size}/${party.maxSize})")
        
        return true
    }
    
    /**
     * Deny party invite
     */
    fun denyInvite(player: Player): Boolean {
        val party = parties.values.find { it.invites.containsKey(player.uniqueId) } ?: run {
            player.sendMessage(TextUtils.format("<red>You have no pending party invites!"))
            return false
        }
        
        party.invites.remove(player.uniqueId)
        player.sendMessage(TextUtils.format("<yellow>Declined party invite"))
        
        val leader = plugin.server.getPlayer(party.leader)
        leader?.sendMessage(TextUtils.format("<yellow>${player.name}</yellow> <gray>declined the party invite"))
        
        return true
    }
    
    /**
     * Leave party
     */
    fun leaveParty(player: Player): Boolean {
        val partyId = playerParties.remove(player.uniqueId) ?: run {
            player.sendMessage(TextUtils.format("<red>You are not in a party!"))
            return false
        }
        
        val party = parties[partyId] ?: return false
        
        if (party.leader == player.uniqueId) {
            // Leader left, disband party
            disbandParty(party)
            return true
        }
        
        party.members.remove(player.uniqueId)
        player.sendMessage(TextUtils.format("<yellow>Left the party"))
        
        broadcastToParty(party, "<yellow>${player.name}</yellow> <gray>left the party <gray>(${party.members.size}/${party.maxSize})")
        
        return true
    }
    
    /**
     * Kick player from party
     */
    fun kickPlayer(kicker: Player, target: Player): Boolean {
        val partyId = playerParties[kicker.uniqueId] ?: run {
            kicker.sendMessage(TextUtils.format("<red>You are not in a party!"))
            return false
        }
        
        val party = parties[partyId] ?: return false
        
        if (party.leader != kicker.uniqueId) {
            kicker.sendMessage(TextUtils.format("<red>Only the party leader can kick players!"))
            return false
        }
        
        if (!party.members.contains(target.uniqueId)) {
            kicker.sendMessage(TextUtils.format("<red>${target.name} is not in your party!"))
            return false
        }
        
        if (target.uniqueId == party.leader) {
            kicker.sendMessage(TextUtils.format("<red>You cannot kick yourself!"))
            return false
        }
        
        party.members.remove(target.uniqueId)
        playerParties.remove(target.uniqueId)
        
        target.sendMessage(TextUtils.format("<red>You were kicked from the party!"))
        broadcastToParty(party, "<yellow>${target.name}</yellow> <red>was kicked from the party")
        
        return true
    }
    
    /**
     * Disband party
     */
    fun disbandParty(party: Party) {
        broadcastToParty(party, "<red>Party disbanded!")
        
        party.members.forEach { uuid ->
            playerParties.remove(uuid)
        }
        
        parties.remove(party.id)
    }
    
    /**
     * Get player's party
     */
    fun getPlayerParty(uuid: UUID): Party? {
        val partyId = playerParties[uuid] ?: return null
        return parties[partyId]
    }
    
    /**
     * Check if player is in party
     */
    fun isInParty(uuid: UUID): Boolean = playerParties.containsKey(uuid)
    
    /**
     * Get party members online
     */
    fun getOnlineMembers(party: Party): List<Player> {
        return party.members.mapNotNull { plugin.server.getPlayer(it) }
    }
    
    /**
     * Broadcast message to party
     */
    fun broadcastToParty(party: Party, message: String) {
        getOnlineMembers(party).forEach { player ->
            player.sendMessage(TextUtils.format("<gray>[<green>Party</green>]</gray> $message"))
        }
    }
    
    /**
     * Party chat
     */
    fun sendPartyChat(sender: Player, message: String) {
        val party = getPlayerParty(sender.uniqueId) ?: run {
            sender.sendMessage(TextUtils.format("<red>You are not in a party!"))
            return
        }
        
        broadcastToParty(party, "<yellow>${sender.name}</yellow><gray>:</gray> <white>$message")
    }
    
    /**
     * Get memory stats
     */
    fun getMemoryStats(): Map<String, Any> {
        return mapOf(
            "active_parties" to parties.size,
            "players_in_parties" to playerParties.size
        )
    }
}

/**
 * Duel challenge data
 */
data class DuelChallenge(
    val challenger: UUID,
    val target: UUID,
    val challengerName: String,
    val targetName: String,
    val kitName: String,
    val createdAt: Long
)

/**
 * Party data class
 */
data class Party(
    val id: UUID,
    val leader: UUID,
    val members: MutableSet<UUID>,
    val invites: MutableMap<UUID, Long> = mutableMapOf(),
    val maxSize: Int = 8,
    val createdAt: Long = System.currentTimeMillis()
)
