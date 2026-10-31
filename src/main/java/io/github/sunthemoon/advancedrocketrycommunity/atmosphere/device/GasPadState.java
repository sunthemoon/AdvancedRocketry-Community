package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.TankData.Gas;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** One immutable publication owns both finite gas and the exact one-item slot states. */
public record GasPadState(Gas gas, int amount, Canister input, Canister output) {
    public enum Canister { NONE, EMPTY, OXYGEN, HYDROGEN }
    private static final Set<String> KEYS = Set.of("schema_version", "gas", "amount", "input", "output");
    public static final GasPadState EMPTY = new GasPadState(Gas.EMPTY, 0, Canister.NONE, Canister.NONE);
    public GasPadState {
        if (gas == null || input == null || output == null || amount < 0 || amount > 16000 || gas == Gas.EMPTY && amount != 0) {
            throw new IllegalArgumentException("Invalid gas pad state");
        }
    }
    public GasPadState process() {
        Gas nextGas = gas; int nextAmount = amount; Canister nextInput = input; Canister nextOutput = output;
        if ((input == Canister.OXYGEN || input == Canister.HYDROGEN) && amount <= 15000
                && (gas == Gas.EMPTY || amount == 0 || gas.name().equals(input.name()))) {
            nextGas = Gas.valueOf(input.name()); nextAmount += 1000; nextInput = Canister.EMPTY;
        }
        if (nextOutput == Canister.EMPTY && nextAmount >= 1000) {
            nextOutput = Canister.valueOf(nextGas.name()); nextAmount -= 1000;
        }
        return new GasPadState(nextGas, nextAmount, nextInput, nextOutput);
    }
    public GasPadState insert(int slot, Canister canister) {
        if (slot == 0 && input == Canister.NONE && (canister == Canister.OXYGEN || canister == Canister.HYDROGEN)) {
            return new GasPadState(gas, amount, canister, output);
        }
        if (slot == 1 && output == Canister.NONE && canister == Canister.EMPTY) { return new GasPadState(gas, amount, input, canister); }
        return this;
    }
    public GasPadState remove(int slot) { return new GasPadState(gas, amount, slot == 0 ? Canister.NONE : input, slot == 1 ? Canister.NONE : output); }
    public GasPadState fill(Gas supplied, int requested) {
        if (supplied == null || supplied == Gas.EMPTY || requested <= 0 || amount != 0 && gas != supplied) { return this; }
        return new GasPadState(supplied, amount + Math.min(requested, 16000 - amount), input, output);
    }
    public CompoundTag encode() {
        CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1); root.putString("gas", gas.name());
        root.putInt("amount", amount); root.putString("input", input.name()); root.putString("output", output.name()); return root;
    }
    public static GasPadState decode(Tag raw) {
        if (!(raw instanceof CompoundTag root) || !DeviceProtection.bounded(root) || !root.getAllKeys().equals(KEYS)
                || !root.contains("schema_version", Tag.TAG_INT) || root.getInt("schema_version") != 1
                || !root.contains("gas", Tag.TAG_STRING) || !root.contains("amount", Tag.TAG_INT)
                || !root.contains("input", Tag.TAG_STRING) || !root.contains("output", Tag.TAG_STRING)) { return null; }
        try { return new GasPadState(Gas.valueOf(root.getString("gas")), root.getInt("amount"),
                Canister.valueOf(root.getString("input")), Canister.valueOf(root.getString("output"))); }
        catch (IllegalArgumentException invalid) { return null; }
    }
}
