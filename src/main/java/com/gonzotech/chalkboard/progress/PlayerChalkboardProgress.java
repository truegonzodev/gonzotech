package com.gonzotech.chalkboard.progress;

import com.gonzotech.chalkboard.core.Quantity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-player progress attachment for Chalkboard / Resonance progression.
 * Maintains persistent formula states and a continuous global chalk drawing
 * that never resets across discovery completions unless explicitly cleared.
 */
public class PlayerChalkboardProgress {

    public static final Codec<PlayerChalkboardProgress> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("currentDiscoveryIndex").forGetter(PlayerChalkboardProgress::getCurrentDiscoveryIndex),
                    Codec.INT.listOf().fieldOf("unlockedRecipeTiers").forGetter(p -> new ArrayList<>(p.getUnlockedRecipeTiers())),
                    Codec.STRING.listOf().fieldOf("unlockedSecretQuantities").forGetter(p -> new ArrayList<>(p.getUnlockedSecretQuantities())),
                    Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("savedExprJson", Map.of()).forGetter(PlayerChalkboardProgress::getSavedExprJsonStr),
                    Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("savedDrawingJson", Map.of()).forGetter(PlayerChalkboardProgress::getSavedDrawingJsonStr),
                    Codec.STRING.optionalFieldOf("globalDrawingJson", "").forGetter(PlayerChalkboardProgress::getGlobalDrawingJson)
            ).apply(instance, PlayerChalkboardProgress::new)
    );

    private int currentDiscoveryIndex;
    private final Set<Integer> unlockedRecipeTiers;
    private final Set<String> unlockedSecretQuantities;
    private final Map<String, String> savedExprJson;
    private final Map<String, String> savedDrawingJson;
    private String globalDrawingJson;

    public PlayerChalkboardProgress() {
        this(0, List.of(), List.of(), Map.of(), Map.of(), "");
    }

    public PlayerChalkboardProgress(int currentDiscoveryIndex, List<Integer> tiers, List<String> secrets,
                                  Map<String, String> savedExpr, Map<String, String> savedDrawing,
                                  String globalDrawingJson) {
        this.currentDiscoveryIndex = Math.max(0, currentDiscoveryIndex);
        this.unlockedRecipeTiers = new HashSet<>(tiers);
        this.unlockedSecretQuantities = new HashSet<>(secrets);
        this.savedExprJson = new HashMap<>(savedExpr);
        this.savedDrawingJson = new HashMap<>(savedDrawing);
        this.globalDrawingJson = globalDrawingJson != null ? globalDrawingJson : "";
    }

    public int getCurrentDiscoveryIndex() {
        return currentDiscoveryIndex;
    }

    public void setCurrentDiscoveryIndex(int index) {
        this.currentDiscoveryIndex = Math.max(0, index);
    }

    public void advanceDiscovery() {
        this.currentDiscoveryIndex++;
    }

    public boolean isInfiniteMode() {
        return currentDiscoveryIndex >= 16;
    }

    public Set<Integer> getUnlockedRecipeTiers() {
        return unlockedRecipeTiers;
    }

    public void unlockRecipeTier(int tier) {
        this.unlockedRecipeTiers.add(tier);
    }

    public boolean isRecipeTierUnlocked(int tier) {
        return unlockedRecipeTiers.contains(tier);
    }

    public Set<String> getUnlockedSecretQuantities() {
        return unlockedSecretQuantities;
    }

    public void unlockSecretQuantity(String id) {
        if (id != null) {
            this.unlockedSecretQuantities.add(id);
        }
    }

    public Map<String, String> getSavedExprJsonStr() {
        return savedExprJson;
    }

    public String getSavedExpr(int discoveryIndex) {
        return savedExprJson.get(String.valueOf(discoveryIndex));
    }

    public void setSavedExpr(int discoveryIndex, String json) {
        if (json != null) {
            savedExprJson.put(String.valueOf(discoveryIndex), json);
        }
    }

    public Map<String, String> getSavedDrawingJsonStr() {
        return savedDrawingJson;
    }

    public String getGlobalDrawingJson() {
        if (globalDrawingJson != null && !globalDrawingJson.isEmpty()) {
            return globalDrawingJson;
        }
        String saved = savedDrawingJson.get(String.valueOf(currentDiscoveryIndex));
        return saved != null ? saved : "";
    }

    public void setGlobalDrawingJson(String json) {
        this.globalDrawingJson = json != null ? json : "";
        this.savedDrawingJson.put(String.valueOf(currentDiscoveryIndex), this.globalDrawingJson);
    }

    public int getTrayQuantityTier() {
        if (currentDiscoveryIndex >= 14) return 99;
        if (currentDiscoveryIndex >= 11) return 4;
        if (currentDiscoveryIndex >= 6) return 3;
        if (currentDiscoveryIndex >= 3) return 2;
        if (currentDiscoveryIndex >= 1) return 1;
        return 0;
    }

    public boolean isQuantityUnlocked(Quantity q) {
        if (q == null) return false;
        if (q.tier() <= getTrayQuantityTier()) return true;
        return unlockedSecretQuantities.contains(q.id());
    }
}
