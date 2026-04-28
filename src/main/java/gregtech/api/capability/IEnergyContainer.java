package gregtech.api.capability;

import net.minecraft.util.EnumFacing;

/**
 * GregTech 能量容器接口 (Stub)。
 * <p>
 * 此文件为编译时 stub，仅供 AE2 引用 IEnergyContainer 类型签名。
 * 运行时由 GregTech 提供完整实现。
 */
public interface IEnergyContainer {

    long acceptEnergyFromNetwork(EnumFacing side, long voltage, long amperage);

    boolean inputsEnergy(EnumFacing side);

    default boolean outputsEnergy(EnumFacing side) {
        return false;
    }

    long changeEnergy(long differenceAmount);

    default long addEnergy(long energyToAdd) {
        return changeEnergy(energyToAdd);
    }

    default long removeEnergy(long energyToRemove) {
        return changeEnergy(-energyToRemove);
    }

    default long getEnergyCanBeInserted() {
        return getEnergyCapacity() - getEnergyStored();
    }

    long getEnergyStored();

    long getEnergyCapacity();

    default long getOutputAmperage() {
        return 0L;
    }

    default long getOutputVoltage() {
        return 0L;
    }

    long getInputAmperage();

    long getInputVoltage();

    default long getInputPerSec() {
        return 0L;
    }

    default long getOutputPerSec() {
        return 0L;
    }

    default boolean isOneProbeHidden() {
        return false;
    }
}
