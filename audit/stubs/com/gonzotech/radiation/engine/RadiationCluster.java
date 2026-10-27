package com.gonzotech.radiation.engine;
/** Real cluster is independently exercised by radiation_engine and inside_exposure. */
public class RadiationCluster {public record Summary(double total){} public static Summary inspect(java.util.List<net.minecraft.world.item.ItemStack> roots){return new Summary(0);}}
