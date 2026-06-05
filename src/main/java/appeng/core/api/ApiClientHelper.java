package appeng.core.api;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.List;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import appeng.api.config.IncludeExclude;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.tile.inventory.IAEStackInventory;
import appeng.api.util.IClientHelper;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import appeng.fluids.util.AEFluidStack;
import appeng.fluids.util.AEFluidStackType;
import appeng.util.ReadableNumberConverter;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

public class ApiClientHelper implements IClientHelper {

    private static final String[] NUMBER_FORMATS = new String[] { "#.000", "#.00", "#.0", "#" };

    @Override
    public void addCellInformation(ICellInventoryHandler handler, List<String> lines) {
        if (handler == null) {
            return;
        }

        final ICellInventory cellInventory = handler.getCellInv();

        if (cellInventory != null) {
            lines.add(
                    Tooltips.bytesUsed(cellInventory.getUsedBytes(), cellInventory.getTotalBytes()).getFormattedText());

            lines.add(Tooltips.typesUsed(cellInventory.getStoredItemTypes(), cellInventory.getTotalItemTypes())
                    .getFormattedText());
        }

        KeyCounter itemList = new KeyCounter();

        if (handler.isPreformatted()) {
            final String list = (handler.getIncludeExcludeMode() == IncludeExclude.WHITELIST ? GuiText.Included
                    : GuiText.Excluded).getLocal();

            if (handler.isFuzzy()) {
                lines.add("[" + GuiText.Partitioned.getLocal() + "]" + " - " + list + ' ' + GuiText.Fuzzy.getLocal());
            } else {
                lines.add("[" + GuiText.Partitioned.getLocal() + "]" + " - " + list + ' ' + GuiText.Precise.getLocal());
            }

            if (handler.isSticky()) {
                lines.add(GuiText.Sticky.getLocal());
            }

            if (Minecraft.getMinecraft().gameSettings.advancedItemTooltips
                    || Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                KeyCounter available = cellInventory.getAvailableKeyCounter();
                for (Object2LongMap.Entry<AEKey> entry : available) {
                    AEKey entryKey = entry.getKey();
                    long entryAmount = entry.getLongValue();
                    if (entryKey instanceof AEItemKey itemKey) {
                        lines.add(itemKey.toStack().getDisplayName() + ": "
                                + ReadableNumberConverter.INSTANCE.toWideReadableForm(entryAmount));
                    } else if (entryKey instanceof AEFluidKey fluidKey) {
                        lines.add(fluidKey.getDisplayName() + ": "
                                + fluidStackSize(entryAmount));
                    }
                }
            }
        } else {
            if (!AEConfig.instance().showCellContentsPreview())
                return;
            if (Minecraft.getMinecraft().gameSettings.advancedItemTooltips
                    || Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                KeyCounter available = cellInventory.getAvailableKeyCounter();
                for (Object2LongMap.Entry<AEKey> entry : available) {
                    AEKey entryKey = entry.getKey();
                    long entryAmount = entry.getLongValue();
                    if (entryKey instanceof AEItemKey itemKey) {
                        lines.add(itemKey.toStack().getDisplayName() + ": "
                                + ReadableNumberConverter.INSTANCE.toWideReadableForm(entryAmount));
                    } else if (entryKey instanceof AEFluidKey fluidKey) {
                        lines.add(fluidKey.getDisplayName() + ": "
                                + fluidStackSize(entryAmount));
                    }
                }
            }
        }
    }

    private String fluidStackSize(long size) {
    String unit;
    if (size >= 1000) {
        unit = "B";
    } else {
        unit = "mB";
    }

    final int log = (int) Math.floor(Math.log10(size)) / 2;

    final int index = Math.max(0, Math.min(3, log));

    final DecimalFormatSymbols symbols = new DecimalFormatSymbols();
    symbols.setDecimalSeparator('.');
    final DecimalFormat format = new DecimalFormat(NUMBER_FORMATS[index]);
    format.setDecimalFormatSymbols(symbols);
    format.setRoundingMode(RoundingMode.DOWN);

    String formatted = format.format(size / 1000d);

    return formatted.concat(unit);
}
}
