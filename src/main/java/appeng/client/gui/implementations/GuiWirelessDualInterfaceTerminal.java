/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui.implementations;

import static appeng.client.render.BlockPosHighlighter.hilightBlock;
import static appeng.helpers.PatternHelper.CRAFTING_GRID_DIMENSION;
import static appeng.helpers.ItemStackHelper.stackFromNBT;
import static appeng.helpers.PatternHelper.PROCESSING_INPUT_WIDTH;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.BiPredicate;

import com.google.common.collect.HashMultimap;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;

import appeng.api.config.ActionItems;
import appeng.api.config.CombineMode;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.client.gui.AEBaseMEGui;
import appeng.client.gui.slots.VirtualMEMonitorableSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.UniversalTerminalButtons;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.gui.widgets.MEGuiTooltipTextField;
import appeng.client.me.ClientDCInternalInv;
import appeng.client.me.ItemRepo;
import appeng.client.me.SlotDisconnected;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerWirelessDualInterfaceTerminal;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.container.slot.SlotPatternOutputs;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.DualityInterface;
import appeng.helpers.InventoryAction;
import appeng.helpers.PatternHelper;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.integration.Integrations;
import appeng.util.BlockPosUtils;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

/**
 * 閺冪姷鍤庢禍灞芥値娑撯偓閹恒儱褰涚紒鍫㈩伂閻ㄥ嚕UI
 *
 * 閻╁瓨甯寸紒褎澹?AEBaseGui閿涘奔绗夐崘宥囨埛閹?GuiInterfaceTerminal閵?
 * 閸欏倽鈧?AE2Things 閻?GuiBaseInterfaceWireless + GuiWirelessDualInterfaceTerminal 閻ㄥ嫭鐏﹂弸鍕剁窗
 * - 娑擃參妫块敍姘复閸欙絽鍨悰銊╂桨閺?+ 閻溾晛顔嶉懗灞藉瘶閿涘牅绮?GuiInterfaceTerminal 缁夌粯顦查惃鍕偓鏄忕帆閿?
 * - 瀹革缚鏅堕敍姝丒閻椻晛鎼х純鎴炵壐閿涘牊妯夌粈绡圗缂冩垹绮舵稉顓犳畱閻椻晛鎼ч敍灞界敨閹兼粎鍌ㄥ鍡楁嫲濠婃艾濮╅弶鈽呯礆
 * - 閸欏厖鏅堕敍姘壉閺夎法绱崘娆忓隘閸╃噦绱欐稉搴濆瘜GUI闁劌鍨庨柌宥呭綌閿涘瘓Size=240閿?
 *
 * 闁插洨鏁?AE2Things 閻ㄥ嫮澹掑▓濠囨桨閺夊灝绔风仦鈧敍姝篠ize = 240閿涘本鐗遍弶鍧楁桨閺夊じ绮?guiLeft+209 瀵偓婵绮崚璁圭礉
 * 娑撳簼瀵?GUI 閺?31px 閻ㄥ嫰鍣搁崣鐘插隘閸╃喆鈧?
 */
public class GuiWirelessDualInterfaceTerminal extends AEBaseMEGui
        implements ContainerWirelessDualInterfaceTerminal.IMEInventoryUpdateReceiver,
        ISortSource, IConfigManagerHost {

    private static final int CRAFTING_INPUT_SLOTS = CRAFTING_GRID_DIMENSION * CRAFTING_GRID_DIMENSION;

    // ========== 鐠愭潙娴樼挧鍕爱 ==========
    private static final ResourceLocation ITEMS_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/gui/widget/items.png");
    private static final ResourceLocation PATTERN_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/gui/widget/pattern.png");
    private static final ResourceLocation PATTERN3_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/gui/widget/pattern3.png");

    // ========== 閹恒儱褰涚紒鍫㈩伂鐢啫鐪敮鎼佸櫤閿涘牅绮?GuiInterfaceTerminal 缁夌粯顦查敍?==========

    /** 閹恒儱褰涢崚妤勩€冮弽鍥暯缁涘娈?X 閸嬪繒些 */
    private static final int OFFSET_X = 21;
    /** 閹恒儱褰涚紒鍫㈩伂娑撹缍嬮惃鍕祼鐎规艾顔旀惔?*/
    private static final int MAIN_GUI_WIDTH = 208;
    /** 閹恒儱褰涚紒鍫㈩伂婢舵挳鍎存妯哄 + 鎼存洟鍎撮懗灞藉瘶妤傛ê瀹抽惃鍕祼鐎规艾鈧?*/
    private static final int MAGIC_HEIGHT_NUMBER = 52 + 99;
    private static final String MOLECULAR_ASSEMBLER = "tile.appliedenergistics2.molecular_assembler";

    // ========== 閺嶉攱婢樼紓鏍у晸闂堛垺婢樼敮鎼佸櫤閿涘牆灏柊?AE2Things pattern.png/pattern3.png 鐠愭潙娴橀敍?==========

    /**
     * 閺嶉攱婢橀棃銏℃緲閸︺劋瀵?GUI 閸愬懐娈戠挧宄邦潗X閸嬪繒些閿涘牏娴夌€?guiLeft閿?
     * 閸欏倽鈧?AE2Things: drawTexturedModalRect(offsetX + 209, ...)
     */
    private static final int PATTERN_PANEL_X_OFFSET = 209;

    /**
     * 閺嶉攱婢橀棃銏℃緲娑撳﹤宕愰柈銊ュ瀻鐏忓搫顕敍鍫濇値閹?婢跺嫮鎮婄純鎴炵壐閸栧搫鐓欓敍?
     * 鐎电懓绨茬拹鏉戞禈 pattern3.png (0,0)閳?33鑴?3 閹?pattern.png (0,93)閳?33鑴?3
     */
    private static final int PATTERN_PANEL_WIDTH = 133;
    private static final int PATTERN_PANEL_UPPER_HEIGHT = 93;

    /**
     * 閺嶉攱婢橀棃銏℃緲娑撳宕愰柈銊ュ瀻鐏忓搫顕敍鍫熺壉閺夌竸N/OUT濡茶棄灏崺鐕傜礆
     * 鐎电懓绨茬拹鏉戞禈 pattern.png (133,0)閳?0鑴?7
     */
    private static final int PATTERN_PANEL_LOWER_WIDTH = 40;
    private static final int PATTERN_PANEL_LOWER_HEIGHT = 77;
    private static final int PATTERN_PANEL_FOOTER_WIDTH = 32;
    private static final int PATTERN_PANEL_FOOTER_HEIGHT = 32;

    /**
     * 閺嶉攱婢橀棃銏℃緲閹鐝惔?
     */
    private static final int PATTERN_PANEL_HEIGHT = PATTERN_PANEL_UPPER_HEIGHT + PATTERN_PANEL_LOWER_HEIGHT;
    private static final int PATTERN_PANEL_TOTAL_HEIGHT = PATTERN_PANEL_HEIGHT + PATTERN_PANEL_FOOTER_HEIGHT;

    // ===== 閸氬牊鍨氱純鎴炵壐閸︺劋绗傞崡濠呭垱閸ュ彞鑵戦惃鍕秴缂冾噯绱欓惄绋款嚠娴滃酣娼伴弶鍨箯娑撳﹨顫楅敍?=====
    /** 閸氬牊鍨氱純鎴炵壐鐠у嘲顫怷/Y閸嬪繒些閿涘牏娴夌€靛綊娼伴弶鍨箯娑撳﹨顫楅敍灞筋嚠姒绘劘鍒涢崶鍙ヨ厬閻?鑴?缂冩垶鐗哥粭顑跨娑擃亝蝎娴ｅ秴涔忔稉濠咁潡閿?*/
    private static final int CRAFTING_GRID_OFFSET_X = 15;
    private static final int CRAFTING_GRID_OFFSET_Y = 18;
    private static final int PROCESSING_GRID_OFFSET_X = 15;
    private static final int PROCESSING_GRID_OFFSET_Y = 9;
    private static final int PROCESSING_INPUT_SCROLLBAR_OFFSET_X = PROCESSING_GRID_OFFSET_X + 4 * 18 + 4;
    private static final int PROCESSING_INPUT_ROWS = 4;

    // ===== 鏉堟挸鍤Σ钘夋躬娑撳﹤宕愮拹鏉戞禈娑擃厾娈戞担宥囩枂 =====
    /**
     * 鏉堟挸鍤Σ鐣屽⒖閸濅焦瑕嗛弻鎾茬秴缂冾噯绱欓惄绋款嚠闂堛垺婢樺锔跨瑐鐟欐帪绱?
     * 閸氬牊鍨氬Ο鈥崇础婢堆勑?(26鑴?6) 鏉堣顢嬮崷銊ㄥ垱閸?(103, 32)閿涘瞼澧块崫浣哥湷娑?+5
     * 婢跺嫮鎮婂Ο鈥崇础 3 娑擃亝鐖ｉ崙鍡樞?(18鑴?8) 娑撳骸鎮庨幋鎰佸蹇氱翻閸戝搫顕?
     */
    private static final int CRAFTING_OUTPUT_OFFSET_X = 108;
    private static final int PROCESSING_OUTPUT_OFFSET_X = 96;
    private static final int PROCESSING_OUTPUT_OFFSET_Y = 9;
    private static final int PROCESSING_OUTPUT_COLUMNS = 1;
    private static final int PROCESSING_OUTPUT_ROWS = 4;
    private static final int PROCESSING_OUTPUT_NORMAL_OFFSET_X = 112;
    private static final int PROCESSING_OUTPUT_INVERTED_OFFSET_X = 15;
    private static final int PROCESSING_INVERTED_GRID_OFFSET_X = 58;

    // ===== 閺嶉攱婢業N/OUT閸︺劋绗呴崡濠呭垱閸ュ彞鑵戦惃鍕秴缂冾噯绱欓惄绋款嚠闂堛垺婢樺锔跨瑐鐟欐帪绱?=====
    /** 缁岃櫣娅ч弽閿嬫緲鏉堟挸鍙嗗Σ鐣屽⒖閸濅焦瑕嗛弻鎾茬秴缂冾噯绱?8鑴?8 閺嶅洤鍣Σ鏂ょ礉鏉堣顢嬮崷銊ョ俺闁劏鍒涢崶?(9,5)閿涘瞼澧块崫?+1閿?*/
    private static final int PATTERN_IN_OFFSET_X = 10;
    private static final int PATTERN_IN_OFFSET_Y = PATTERN_PANEL_UPPER_HEIGHT + 6;

    /** 缂傛牜鐖滈弽閿嬫緲鏉堟挸鍤Σ鐣屽⒖閸濅焦瑕嗛弻鎾茬秴缂冾噯绱?4鑴?4 婢堆勑敍宀冪珶濡楀棗婀惔鏇㈠劥鐠愭潙娴?(7,45)閿涘瞼澧块崫浣哥湷娑?+4閿?*/
    private static final int PATTERN_OUT_OFFSET_X = 11;
    private static final int PATTERN_OUT_OFFSET_Y = PATTERN_PANEL_UPPER_HEIGHT + 49;

    // ========== ME閻椻晛鎼ч棃銏℃緲鐢悂鍣洪敍鍫濆棘閼?AE2Things: 4x4 缂冩垶鐗? 101鐎硅棄瀹? 96妤傛ê瀹? 瀹革缚绗呯憴鎺戭嚠姒绘劧绱?==========
    /** ME閻椻晛鎼ч棃銏℃緲閻ㄥ嫬顔旀惔锔肩礄娑?AE2Things 閻?101 娑撯偓閼疯揪绱?*/
    private static final int ITEM_PANEL_WIDTH = 101;
    /** ME閻椻晛鎼ч棃銏℃緲濮ｅ繐鍨弰鍓с仛閻ㄥ嫯顢戦弫?*/
    private static final int ITEM_PANEL_ROWS = 4;
    /** ME閻椻晛鎼ч棃銏℃緲濮ｅ繗顢戦弰鍓с仛閻ㄥ嫬鍨弫?*/
    private static final int ITEM_PANEL_COLS = 4;
    /** ME閻椻晛鎼ч棃銏℃緲閸愬懐缍夐弽鑲╂祲鐎靛綊娼伴弶鍨箯娑撳﹨顫楅惃鍒嬮崑蹇曅?*/
    private static final int ITEM_GRID_OFFSET_X = 5;
    /** ME閻椻晛鎼ч棃銏℃緲閸愬懐缍夐弽鑲╂祲鐎靛綊娼伴弶鍨箯娑撳﹨顫楅惃鍒岄崑蹇曅╅敍鍫熸偝缁便垺顢嬫稉瀣煙閿?*/
    private static final int ITEM_GRID_OFFSET_Y = 18;
    /** ME閻椻晛鎼ч棃銏℃緲閹鐝惔锔肩礄娑?AE2Things 閻?96 娑撯偓閼疯揪绱?*/
    private static final int ITEM_PANEL_HEIGHT = 96;

    // ========== 閹恒儱褰涚紒鍫㈩伂閺佺増宓侀敍鍫滅矤 GuiInterfaceTerminal 缁夌粯顦查敍?==========

    // To make JEI look nicer. Otherwise, the buttons will make JEI in a strange place.
    private final int jeiOffset = Platform.isJEIEnabled() ? 24 : 0;

    private final HashMap<Long, ClientDCInternalInv> byId = new HashMap<>();
    private final Map<Long, ClientDCInternalInv> providerById = new HashMap<>();

    private final HashMultimap<String, ClientDCInternalInv> byName = HashMultimap.create();
    private final HashMap<ClientDCInternalInv, BlockPos> blockPosHashMap = new HashMap<>();
    private final HashMap<GuiButton, ClientDCInternalInv> guiButtonHashMap = new HashMap<>();
    private final HashMap<GuiButton, ClientDCInternalInv> doubleButtonHashMap = new HashMap<>();
    private final Map<ClientDCInternalInv, Integer> numUpgradesMap = new HashMap<>();
    private final ArrayList<String> names = new ArrayList<>();
    private final ArrayList<Object> lines = new ArrayList<>();
    private final Set<Object> matchedStacks = new HashSet<>();
    private final Map<String, Set<Object>> cachedSearches = new WeakHashMap<>();
    private final Map<ClientDCInternalInv, Integer> dimHashMap = new HashMap<>();

    /** 閹恒儱褰涚紒鍫㈩伂閻ㄥ嫭鎮崇槐銏☆攱 */
    private final MEGuiTooltipTextField searchFieldOutputs;
    private final MEGuiTooltipTextField searchFieldInputs;
    private final MEGuiTooltipTextField searchFieldNames;

    /** 閹恒儱褰涚紒鍫㈩伂閻ㄥ嫬濮涢懗鑺ュ瘻闁?*/
    private final GuiImgButton guiButtonHideFull;
    private final GuiImgButton guiButtonAssemblersOnly;
    private final GuiImgButton guiButtonBrokenRecipes;
    private final GuiImgButton terminalStyleBox;

    private boolean refreshList = false;

    /* These are worded so that the intended default is false */
    private boolean onlyShowWithSpace = false;
    private boolean onlyMolecularAssemblers = false;
    private boolean onlyBrokenRecipes = false;
    /** 閹恒儱褰涢崚妤勩€冮惃鍕讲鐟欎浇顢戦弫?*/
    private int rows = 6;

    // ========== ME閻椻晛鎼ч棃銏℃緲閹兼粎鍌ㄥ鍡氼唶韫囧棙鏋冮張?==========
    private static String memoryText = "";

    // ========== 閺嶉攱婢橀棃銏℃緲閹稿鎸?==========
    private GuiTabButton tabCraftButton;
    private GuiTabButton tabProcessButton;
    private GuiImgButton substitutionsEnabledBtn;
    private GuiImgButton substitutionsDisabledBtn;
    private GuiImgButton beSubstitutionsEnabledBtn;
    private GuiImgButton beSubstitutionsDisabledBtn;
    private GuiImgButton invertBtn;
    private GuiImgButton combineEnabledBtn;
    private GuiImgButton combineDisabledBtn;
    private GuiImgButton encodeBtn;
    private GuiImgButton clearBtn;

    // ========== 閺佷即鍣虹拫鍐╂殻閹稿鎸抽敍鍫濐槱閻炲棙膩瀵繋绗呴弰鍓с仛閿涘奔绗?GuiPatternTerm 娑撯偓閼疯揪绱?==========
    private GuiImgButton x2Btn;
    private GuiImgButton x3Btn;
    private GuiImgButton plusOneBtn;
    private GuiImgButton divTwoBtn;
    private GuiImgButton divThreeBtn;
    private GuiImgButton minusOneBtn;
    private GuiImgButton doubleBtn;
    private UniversalTerminalButtons universalButtons;

    // ========== Crafting Status 閹稿鎸?==========
    private GuiTabButton craftingStatusBtn;

    // ========== ME閻椻晛鎼ч棃銏℃緲閹烘帒绨?鏉╁洦鎶ら幐澶愭尦閿涘牆寮懓?GuiMEMonitorable閿?==========
    private GuiImgButton SortByBox;
    private GuiImgButton SortDirBox;
    private GuiImgButton ViewBox;
    private GuiImgButton searchBoxSettings;

    // ========== ME閻椻晛鎼ч棃銏℃緲閺佺増宓?==========
    private final ItemRepo itemRepo;
    private final GuiScrollbar itemPanelScrollbar;
    private MEGuiTextField itemSearchField;

    // ========== 婢跺嫮鎮婂Ο鈥崇础鏉堟挸鍤Σ鐣岀倳妞ゅ灚绮撮崝銊︽蒋 ==========
    private final GuiScrollbar processingInputScrollbar;
    private int processingInputPage = 0;
    private final GuiScrollbar processingScrollBar;

    private final IConfigManager configSrc;
    private final WirelessTerminalGuiObject wirelessGuiObject;

    // ========== PlacePattern閿涘牏绱惍浣告倵閼奉亜濮╅弨鎯у弳閹恒儱褰涢敍?==========
    /** 瑜版挾绱惍浣瑰瘻闁筋喗瀵滄稉瀣 Alt 鐞氼偅瀵滄担蹇ョ礉鐠佸墽鐤嗘稉?true閿涘奔绗呮稉鈧敮?tick 閺冭泛鐨剧拠鏇熸杹缂?*/
    private boolean pendingPlacePattern = false;

    // ========== 闂堛垺婢橀幏鏍ㄥ閻樿埖鈧?==========
    /** 瀹革缚鏅禡E閻椻晛鎼ч棃銏℃緲閻ㄥ嫭瀚嬮幏鐣屽Ц閹?*/
    private PanelDragState itemPanelDragState;
    /** 閸欏厖鏅堕弽閿嬫緲闂堛垺婢橀惃鍕珛閹风晫濮搁幀?*/
    private PanelDragState patternPanelDragState;

    public GuiWirelessDualInterfaceTerminal(final InventoryPlayer inventoryPlayer,
            final WirelessTerminalGuiObject te) {
        super(new ContainerWirelessDualInterfaceTerminal(inventoryPlayer, te));
        this.wirelessGuiObject = te;

        // xSize = 240閿涘奔绗?AE2Things 娑撯偓閼疯揪绱濇担鎸庣壉閺夊潡娼伴弶鍨讲娴犮儰绗屾稉?GUI 闁插秴褰?
        this.xSize = 240;
        this.ySize = 255;

        // 閹恒儱褰涚紒鍫㈩伂濠婃艾濮╅弶?
        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);

        // 閹恒儱褰涚紒鍫㈩伂閹兼粎鍌ㄥ鍡礄娴?GuiInterfaceTerminal 缁夌粯顦查敍?
        searchFieldInputs = createInterfaceTextField(86, 12, ButtonToolTips.SearchFieldInputs.getLocal());
        searchFieldOutputs = createInterfaceTextField(86, 12, ButtonToolTips.SearchFieldOutputs.getLocal());
        searchFieldNames = createInterfaceTextField(71, 12, ButtonToolTips.SearchFieldNames.getLocal());

        // 閹恒儱褰涚紒鍫㈩伂閸旂喕鍏橀幐澶愭尦
        guiButtonAssemblersOnly = new GuiImgButton(0, 0, Settings.ACTIONS, null);
        guiButtonHideFull = new GuiImgButton(0, 0, Settings.ACTIONS, null);
        guiButtonBrokenRecipes = new GuiImgButton(0, 0, Settings.ACTIONS, null);
        terminalStyleBox = new GuiImgButton(0, 0, Settings.TERMINAL_STYLE, null);

        // 娴?Container 閼惧嘲褰囬柊宥囩枂缁狅紕鎮婇崳銊ф暏娴滃孩甯撴惔?鐟欏棗娴樼拋鍓х枂
        this.configSrc = ((IConfigurableObject) this.inventorySlots).getConfigManager();

        // 閸掓繂顫愰崠鏍т箯娓?ME 閻椻晛鎼ч棃銏℃緲閻ㄥ嫭绮撮崝銊︽蒋閸?ItemRepo
        this.itemPanelScrollbar = new GuiScrollbar();
        this.itemRepo = new ItemRepo(this.itemPanelScrollbar, this);
        this.itemRepo.setRowSize(ITEM_PANEL_COLS);

        // 閸掓繂顫愰崠鏍ь槱閻炲棙膩瀵繗绶崙鐑樞紙濠氥€夊姘З閺?
        this.processingInputScrollbar = new GuiScrollbar();
        this.processingScrollBar = new GuiScrollbar();

        // 閸掓繂顫愰崠鏍桨閺夋寧瀚嬮幏鐣屽Ц閹?
        this.initDragStates();

        // 濞夈劌鍞介懛顏囬煩娑?ME 鎼存挸鐡ㄩ弴瀛樻煀閹恒儲鏁归懓?
        getDualContainer().setMeGui(this);
    }

    /**
     * 閸掓稑缂撻幒銉ュ經缂佸牏顏幖婊呭偍濡楀棴绱欓弬鍥ㄦ拱閸欐ɑ娲块弮璺哄煕閺傜増甯撮崣锝呭灙鐞涱煉绱?
     */
    private MEGuiTooltipTextField createInterfaceTextField(final int width, final int height, final String tooltip) {
        MEGuiTooltipTextField textField = new MEGuiTooltipTextField(width, height, tooltip) {
            @Override
            public void onTextChange(String oldText) {
                refreshList();
            }
        };
        textField.setEnableBackgroundDrawing(false);
        textField.setMaxStringLength(25);
        textField.setTextColor(0xFFFFFF);
        textField.setCursorPositionZero();
        return textField;
    }

    private ContainerWirelessDualInterfaceTerminal getDualContainer() {
        return (ContainerWirelessDualInterfaceTerminal) this.inventorySlots;
    }

    // ========== IMEInventoryUpdateReceiver 閹恒儱褰涚€圭偟骞?==========

    /**
     * 閹恒儲鏁归弶銉ㄥ殰閺堝秴濮熺粩顖滄畱 ME 缂冩垹绮舵惔鎾崇摠閺囧瓨鏌婇敍宀冩祮閸欐垵鍩?ItemRepo
     */
    @Override
    public void postUpdate(final List<IAEStack<?>> list) {
        for (final IAEStack<?> is : list) {
            this.itemRepo.postUpdate(is);
        }
        this.itemRepo.updateView();
        this.updateItemPanelScrollbar();
    }

    // ========== 闂堛垺婢橀崸鎰垼鐠侊紕鐣绘潏鍛И閺傝纭?==========

    /**
     * 閼惧嘲褰囬弽閿嬫緲闂堛垺婢橀惃鍕崳婵獋閸ф劖鐖ｉ敍鍫㈡祲鐎甸€涚艾guiLeft閿?
     * 娴ｈ法鏁?AE2Things 閻ㄥ嫬绔风仦鈧敍姘舵桨閺夊じ绮?guiLeft + 209 瀵偓婵?
     */
    private int getPatternPanelX() {
        return PATTERN_PANEL_X_OFFSET + this.patternPanelDragState.getDragOffsetX();
    }

    /**
     * 閼惧嘲褰囬弽閿嬫緲闂堛垺婢橀惃鍕崳婵獌閸ф劖鐖ｉ崑蹇曅╅敍鍫㈡祲鐎甸€涚艾guiTop閿?
     */
    private int getPatternPanelY() {
        return this.patternPanelDragState.getDragOffsetY();
    }

    /**
     * 閼惧嘲褰嘙E閻椻晛鎼ч棃銏℃緲閻ㄥ嫮绮风€电瓩閸ф劖鐖ｉ敍鍫濈潌楠炴洖娼楅弽鍥风礆
     * 閸欏倽鈧?AE2Things: absX = guiLeft - 101
     */
    private int getItemPanelAbsX() {
        return this.guiLeft - ITEM_PANEL_WIDTH + this.itemPanelDragState.getDragOffsetX();
    }

    /**
     * 閼惧嘲褰嘙E閻椻晛鎼ч棃銏℃緲閻ㄥ嫮绮风€电瓬閸ф劖鐖ｉ敍鍫濈潌楠炴洖娼楅弽鍥风礆
     * 閸欏倽鈧?AE2Things: absY = guiTop + ySize - 96閿涘牆涔忔稉瀣潡鐎靛綊缍堥敍?
     */
    private int getItemPanelAbsY() {
        return this.guiTop + this.ySize - ITEM_PANEL_HEIGHT + this.itemPanelDragState.getDragOffsetY();
    }

    /**
     * 閼惧嘲褰嘙E閻椻晛鎼ч棃銏℃緲閻ㄥ嫯鎹ｆ慨濯傞崸鎰垼閿涘牏娴夌€甸€涚艾guiLeft閿涘瞼鏁ゆ禍搴ゆ珓閹风喐蝎娴ｅ秴鐣炬担宥忕礆
     */
    private int getItemPanelRelX() {
        return -ITEM_PANEL_WIDTH + this.itemPanelDragState.getDragOffsetX();
    }

    /**
     * 閼惧嘲褰嘙E閻椻晛鎼ч棃銏℃緲閻ㄥ嫯鎹ｆ慨濯冮崸鎰垼閿涘牏娴夌€甸€涚艾guiTop閿涘瞼鏁ゆ禍搴ゆ珓閹风喐蝎娴ｅ秴鐣炬担宥忕礆
     */
    private int getItemPanelRelY() {
        return this.ySize - ITEM_PANEL_HEIGHT + this.itemPanelDragState.getDragOffsetY();
    }

    // ========== 閹恒儱褰涚紒鍫㈩伂濠婃艾濮╅弶陇顔曠純?==========

    private void setInterfaceScrollBar() {
        this.getScrollBar().setTop(52).setLeft(189).setHeight(this.rows * 18 - 2);
        this.getScrollBar().setRange(0, this.lines.size() - 1, 1);
    }

    // ========== ME閻椻晛鎼ч棃銏℃緲濠婃艾濮╅弶鈩冩纯閺?==========

    private void updateItemPanelScrollbar() {
        this.itemPanelScrollbar.setRange(0,
                (this.itemRepo.size() + ITEM_PANEL_COLS - 1) / ITEM_PANEL_COLS - ITEM_PANEL_ROWS,
                Math.max(1, ITEM_PANEL_ROWS / 6));
    }

    // ========== 濡叉垝缍呴柌宥呯暰娴?==========

    /**
     * 闁插秴鐣炬担宥嗗閺堝蝎娴ｅ秲鈧?
     * 閺嶉攱婢樼紓鏍у晸閻╃鍙уΣ鎴掔秴娴ｈ法鏁?AE2Things 閻ㄥ嫬鍙曞蹇ョ窗ySize + getY() - viewHeight - 78 - 4
     * 閸忔湹鑵?viewHeight = rows * 18閿涘牊甯撮崣锝呭灙鐞涖劌褰茬憴浣稿隘閸╃喖鐝惔锔肩礆
     * 閻溾晛顔嶉懗灞藉瘶閸滃苯鍙炬禒鏍ㄧ垼閸戝棙蝎娴ｅ秳濞囬悽顭掔窗ySize + getY() - 78 - 7
     */
    private void repositionSlots() {
        final int panelX = getPatternPanelX();
        final int panelY = getPatternPanelY();
        final int viewHeight = this.rows * 18;

        for (final Object obj : this.inventorySlots.inventorySlots) {
            if (obj instanceof AppEngSlot slot) {
                if (slot instanceof SlotFakeCraftingMatrix) {
                    // 3x3閸氬牊鍨氱純鎴炵壐濡叉垝缍?
                    final int craftIdx = slot.getSlotIndex();
                    if (getDualContainer().isCraftingMode()) {
                        if (craftIdx >= CRAFTING_INPUT_SLOTS) {
                            slot.xPos = -9000;
                            slot.yPos = -9000;
                        } else {
                            final int gridX = craftIdx % CRAFTING_GRID_DIMENSION;
                            final int gridY = craftIdx / CRAFTING_GRID_DIMENSION;
                            slot.xPos = panelX + CRAFTING_GRID_OFFSET_X + gridX * 18;
                            slot.yPos = panelY + CRAFTING_GRID_OFFSET_Y + gridY * 18;
                        }
                    } else {
                        final boolean inverted = getDualContainer().isInverted();
                        final int processingGridOffsetX = inverted
                                ? PROCESSING_INVERTED_GRID_OFFSET_X
                                : PROCESSING_GRID_OFFSET_X;
                        final int pageStart = this.processingInputPage * PatternHelper.PROCESSING_INPUT_PAGE_SLOTS;
                        final int pageEnd = Math.min(pageStart + PatternHelper.PROCESSING_INPUT_PAGE_SLOTS,
                                PatternHelper.PROCESSING_INPUT_LIMIT);
                        if (craftIdx < pageStart || craftIdx >= pageEnd) {
                            slot.xPos = -9000;
                            slot.yPos = -9000;
                        } else {
                            final int visibleIndex = craftIdx - pageStart;
                            final int gridX = visibleIndex % PROCESSING_INPUT_WIDTH;
                            final int gridY = visibleIndex / PROCESSING_INPUT_WIDTH;
                            slot.xPos = panelX + processingGridOffsetX + gridX * 18;
                            slot.yPos = panelY + PROCESSING_GRID_OFFSET_Y + gridY * 18;
                        }
                    }
                } else if (slot instanceof SlotPatternTerm) {
                    // 閺嶉攱婢樼紓鏍垳鏉堟挸鍤Σ鏂ょ礄閸氬牊鍨氬Ο鈥崇础娑撳娈戞径褏绮ㄩ弸婊勑?26鑴?6閿?
                    // 閻椻晛鎼у〒鍙夌厠娴ｅ秶鐤?= 婢堆勑潏瑙勵攱(103,32) + 鐏炲懍鑵戦崑蹇曅?5,5)
                    if (getDualContainer().isCraftingMode()) {
                        slot.xPos = panelX + CRAFTING_OUTPUT_OFFSET_X;
                        slot.yPos = panelY + 37;
                    } else {
                        slot.xPos = -9000;
                        slot.yPos = -9000;
                    }
                } else if (slot instanceof SlotPatternOutputs) {
                    // 婢跺嫮鎮婂Ο鈥崇础閻ㄥ嫯绶崙鐑樞敍?娑擃亝鐖ｉ崙?18鑴?8 濡叉垝缍呴敍?
                    if (getDualContainer().isCraftingMode()) {
                        slot.xPos = -9000;
                        slot.yPos = -9000;
                    } else {
                        final int processingOutputOffsetX = getDualContainer().isInverted()
                                ? PROCESSING_OUTPUT_INVERTED_OFFSET_X
                                : PROCESSING_OUTPUT_NORMAL_OFFSET_X;
                        final int outIdx = slot.getSlotIndex();
                        final int outX = outIdx % PROCESSING_OUTPUT_COLUMNS;
                        final int outY = outIdx / PROCESSING_OUTPUT_COLUMNS;
                        slot.xPos = panelX + processingOutputOffsetX + outX * 18;
                        slot.yPos = panelY + PROCESSING_OUTPUT_OFFSET_Y + outY * 18;
                    }
                } else if (slot instanceof SlotRestrictedInput restrictedSlot) {
                    // 閸栧搫鍨庣粚铏规閺嶉攱婢樻潏鎾冲弳濡茶棄鎷扮紓鏍垳閺嶉攱婢樻潏鎾冲毉濡?
                    if (restrictedSlot.getPlaceableItemType()
                            == SlotRestrictedInput.PlacableItemType.BLANK_PATTERN) {
                        slot.xPos = panelX + PATTERN_IN_OFFSET_X;
                        slot.yPos = panelY + PATTERN_IN_OFFSET_Y;
                    } else if (restrictedSlot.getPlaceableItemType()
                            == SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN) {
                        slot.xPos = panelX + PATTERN_OUT_OFFSET_X;
                        slot.yPos = panelY + PATTERN_OUT_OFFSET_Y;
                    } else {
                        // 閸忔湹绮?SlotRestrictedInput閿涘牆顩ч弮鐘靛殠缂佸牏顏崡鍥╅獓濡叉枻绱氶敍灞煎▏閻劍鐖ｉ崙鍡涒偓鏄忕帆
                        slot.yPos = this.ySize + slot.getY() - 78 - 7;
                        slot.xPos = slot.getX() + 14;
                    }
                } else {
                    // 閻溾晛顔嶉懗灞藉瘶濡叉垝缍呴崪灞藉従娴犳牗鐖ｉ崙鍡樞担?
                    slot.yPos = this.ySize + slot.getY() - 78 - 7;
                    slot.xPos = slot.getX() + 14;
                }
            }
        }
    }

    // ========== GUI 閸掓繂顫愰崠?==========

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        // ===== 鐠侊紕鐣婚幒銉ュ經閸掓銆冪悰灞炬殶閿涘牅绗?GuiInterfaceTerminal 闁槒绶稉鈧懛杈剧礆 =====
        final int jeiSearchOffset = Platform.isJEICenterSearchBarEnabled() ? 40 : 0;
        final int maxScreenRows = (int) Math.floor(
                (double) (this.height - MAGIC_HEIGHT_NUMBER - jeiSearchOffset) / 18);

        final Enum<?> terminalStyle = AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE);

        if (terminalStyle == TerminalStyle.FULL) {
            this.rows = maxScreenRows;
        } else if (terminalStyle == TerminalStyle.TALL) {
            this.rows = (int) Math.ceil(maxScreenRows * 0.75);
        } else if (terminalStyle == TerminalStyle.MEDIUM) {
            this.rows = (int) Math.ceil(maxScreenRows * 0.5);
        } else if (terminalStyle == TerminalStyle.SMALL) {
            this.rows = (int) Math.ceil(maxScreenRows * 0.25);
        } else {
            this.rows = maxScreenRows;
        }

        this.rows = Math.min(this.rows, Integer.MAX_VALUE);
        this.rows = Math.max(this.rows, 6);

        super.initGui();

        // ===== 鐠侊紕鐣?ySize 閸?guiTop =====
        this.ySize = MAGIC_HEIGHT_NUMBER + this.rows * 18;
        final int unusedSpace = this.height - this.ySize;
        this.guiTop = (int) Math.floor(unusedSpace / (unusedSpace < 0 ? 3.8f : 2.0f));

        // ===== 閹恒儱褰涚紒鍫㈩伂閹兼粎鍌ㄥ鍡楃暰娴?=====
        searchFieldInputs.x = guiLeft + 32;
        searchFieldInputs.y = guiTop + 25;
        searchFieldOutputs.x = guiLeft + 32;
        searchFieldOutputs.y = guiTop + 38;
        searchFieldNames.x = guiLeft + 32 + 99;
        searchFieldNames.y = guiTop + 38;

        searchFieldNames.setFocused(true);

        // ===== 閹恒儱褰涚紒鍫㈩伂閸旂喕鍏橀幐澶愭尦鐎规矮缍?=====
        terminalStyleBox.x = guiLeft - 18;
        terminalStyleBox.y = guiTop + 8 + this.jeiOffset;
        guiButtonBrokenRecipes.x = guiLeft - 18;
        guiButtonBrokenRecipes.y = terminalStyleBox.y + 20;
        guiButtonHideFull.x = guiLeft - 18;
        guiButtonHideFull.y = guiButtonBrokenRecipes.y + 20;
        guiButtonAssemblersOnly.x = guiLeft - 18;
        guiButtonAssemblersOnly.y = guiButtonHideFull.y + 20;

        // ===== 閹恒儱褰涚紒鍫㈩伂濠婃艾濮╅弶?=====
        this.setInterfaceScrollBar();
        this.repositionSlots();

        // ===== Crafting Status 閹稿鎸抽敍鍫滃瘜閻ｅ矂娼伴崣鍏呯瑐鐟欐帪绱?=====
        this.craftingStatusBtn = new GuiTabButton(this.guiLeft + 170, this.guiTop - 4,
                2 + 11 * 16, GuiText.CraftingStatus.getLocal(), this.itemRender);
        this.craftingStatusBtn.setHideEdge(13);
        this.buttonList.add(this.craftingStatusBtn);

        // ===== 閺嶉攱婢橀棃銏℃緲閹稿鎸抽敍鍫滅秴缂冾喖灏柊?AE2Things 閻?PatternPanel閿?=====
        final int panelScreenX = this.guiLeft + getPatternPanelX();
        final int panelScreenY = this.guiTop + getPatternPanelY();

        // 缂傛牜鐖滈幐澶愭尦閿涘牓娼伴弶鍨敶 (11, 118)閿涘苯婀稉瀣磹閸栧搫鐓欓敍灞肩瑢 AE2Things 娑撯偓閼疯揪绱?
        this.encodeBtn = new GuiImgButton(panelScreenX + 11, panelScreenY + 118,
                Settings.ACTIONS, ActionItems.ENCODE);
        this.buttonList.add(this.encodeBtn);

        // 濞撳懘娅庨幐澶愭尦閿涘牆宕愮亸鍝勵嚟閿涘矂娼伴弶鍨敶 (87, 10)閿涘奔绗傞崡濠傚隘閸╃喎褰告笟褝绱?
        this.clearBtn = new GuiImgButton(panelScreenX + 87, panelScreenY + 10,
                Settings.ACTIONS, ActionItems.CLOSE);
        this.clearBtn.setHalfSize(true);
        this.buttonList.add(this.clearBtn);

        // 閸氬牊鍨?婢跺嫮鎮婂Ο鈥崇础閸掑洦宕查弽鍥╊劮閿涘牓娼伴弶鍨敶 (39, 93)閿涘奔绗傛稉瀣╂唉閻ｅ苯顦╅敍?
        this.tabCraftButton = new GuiTabButton(panelScreenX + 39, panelScreenY + 93,
                new ItemStack(Blocks.CRAFTING_TABLE),
                GuiText.CraftingPattern.getLocal(), this.itemRender);
        this.buttonList.add(this.tabCraftButton);

        this.tabProcessButton = new GuiTabButton(panelScreenX + 39, panelScreenY + 93,
                new ItemStack(Blocks.FURNACE),
                GuiText.ProcessingPattern.getLocal(), this.itemRender);
        this.buttonList.add(this.tabProcessButton);

        // 閺囧じ鍞崫浣哥磻閸忚櫕瀵滈柦顕嗙礄闂堛垺婢橀崘?(97, 10)閿涘奔绗傞崡濠傚隘閸╃喎褰告笟褝绱濋崡濠傛槀鐎甸潻绱?
        this.substitutionsEnabledBtn = new GuiImgButton(panelScreenX + 97, panelScreenY + 10,
                Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.substitutionsEnabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsEnabledBtn);

        this.substitutionsDisabledBtn = new GuiImgButton(panelScreenX + 97, panelScreenY + 10,
                Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.substitutionsDisabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsDisabledBtn);

        // 缂佹繂顕弴鎸庡床閹稿鎸抽敍鍫ユ桨閺夊灝鍞?(87, 20)閿涘本娴涙禒锝呮惂娑撳鏌熼敍灞藉磹鐏忓搫顕敍?
        this.beSubstitutionsEnabledBtn = new GuiImgButton(panelScreenX + 87, panelScreenY + 20,
                Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.beSubstitutionsEnabledBtn.setHalfSize(true);
        this.buttonList.add(this.beSubstitutionsEnabledBtn);

        this.beSubstitutionsDisabledBtn = new GuiImgButton(panelScreenX + 87, panelScreenY + 20,
                Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.beSubstitutionsDisabledBtn.setHalfSize(true);
        this.buttonList.add(this.beSubstitutionsDisabledBtn);

        // 閸欏秷娴嗛幐澶愭尦閿涘牓娼伴弶鍨敶 (97, 20)閿涘苯宕愮亸鍝勵嚟閿涘苯顦╅悶鍡樐佸蹇庣瑓閸欘垵顫嗛敍?
        this.invertBtn = new GuiImgButton(panelScreenX + 97, panelScreenY + 20,
                Settings.ACTIONS, ActionItems.CLOSE);
        this.invertBtn.setHalfSize(true);
        this.buttonList.add(this.invertBtn);

        // 閸氬牆鑻熷Ο鈥崇础閹稿鎸抽敍鍫ユ桨閺夊灝鍞?(87, 30)閿涘苯宕愮亸鍝勵嚟閿涘苯顦╅悶鍡樐佸蹇庣瑓閸欘垵顫嗛敍?
        this.combineEnabledBtn = new GuiImgButton(panelScreenX + 87, panelScreenY + 30,
                Settings.ACTIONS, CombineMode.ENABLED);
        this.combineEnabledBtn.setHalfSize(true);
        this.buttonList.add(this.combineEnabledBtn);

        this.combineDisabledBtn = new GuiImgButton(panelScreenX + 87, panelScreenY + 30,
                Settings.ACTIONS, CombineMode.DISABLED);
        this.combineDisabledBtn.setHalfSize(true);
        this.buttonList.add(this.combineDisabledBtn);

        // ===== 閺佷即鍣虹拫鍐╂殻閹稿鎸抽敍鍫濐槱閻炲棙膩瀵繋绗呴弰鍓с仛閿涘本鏂侀崷銊ㄧ翻閸戠儤蝎閸欏厖鏅堕敍?=====
        final int adjBtnX1 = panelScreenX + PROCESSING_OUTPUT_OFFSET_X + 38;
        final int adjBtnX2 = panelScreenX + PROCESSING_OUTPUT_OFFSET_X + 28;

        this.x3Btn = new GuiImgButton(adjBtnX1, panelScreenY + 6,
                Settings.ACTIONS, ActionItems.MULTIPLY_BY_THREE);
        this.x3Btn.setHalfSize(true);
        this.buttonList.add(this.x3Btn);

        this.x2Btn = new GuiImgButton(adjBtnX1, panelScreenY + 16,
                Settings.ACTIONS, ActionItems.MULTIPLY_BY_TWO);
        this.x2Btn.setHalfSize(true);
        this.buttonList.add(this.x2Btn);

        this.plusOneBtn = new GuiImgButton(adjBtnX1, panelScreenY + 26,
                Settings.ACTIONS, ActionItems.INCREASE_BY_ONE);
        this.plusOneBtn.setHalfSize(true);
        this.buttonList.add(this.plusOneBtn);

        this.divThreeBtn = new GuiImgButton(adjBtnX2, panelScreenY + 6,
                Settings.ACTIONS, ActionItems.DIVIDE_BY_THREE);
        this.divThreeBtn.setHalfSize(true);
        this.buttonList.add(this.divThreeBtn);

        this.divTwoBtn = new GuiImgButton(adjBtnX2, panelScreenY + 16,
                Settings.ACTIONS, ActionItems.DIVIDE_BY_TWO);
        this.divTwoBtn.setHalfSize(true);
        this.buttonList.add(this.divTwoBtn);

        this.minusOneBtn = new GuiImgButton(adjBtnX2, panelScreenY + 26,
                Settings.ACTIONS, ActionItems.DECREASE_BY_ONE);
        this.minusOneBtn.setHalfSize(true);
        this.buttonList.add(this.minusOneBtn);

        // 缂堣鈧?閸戝繐宕愰幐澶愭尦閿涘牆涔忛柨顔?/鑴?閿涘苯褰搁柨顔?/姊?閿?
        this.doubleBtn = new GuiImgButton(adjBtnX2, panelScreenY + 36,
                Settings.ACTIONS, ActionItems.DOUBLE_STACKS);
        this.doubleBtn.setHalfSize(true);
        this.buttonList.add(this.doubleBtn);

        // ===== ME閻椻晛鎼ч棃銏℃緲 =====
        final int itemAbsX = getItemPanelAbsX();
        final int itemAbsY = getItemPanelAbsY();
        final int itemRelX = getItemPanelRelX();
        final int itemRelY = getItemPanelRelY();

        // ME閻椻晛鎼ч棃銏℃緲閹烘帒绨?鏉╁洦鎶ら幐澶愭尦
        int sortBtnOffset = itemAbsY + 18;

        this.SortByBox = new GuiImgButton(itemAbsX - 18, sortBtnOffset, Settings.SORT_BY,
                this.configSrc.getSetting(Settings.SORT_BY));
        this.buttonList.add(this.SortByBox);
        sortBtnOffset += 20;

        this.ViewBox = new GuiImgButton(itemAbsX - 18, sortBtnOffset, Settings.VIEW_MODE,
                this.configSrc.getSetting(Settings.VIEW_MODE));
        this.buttonList.add(this.ViewBox);
        sortBtnOffset += 20;

        this.SortDirBox = new GuiImgButton(itemAbsX - 18, sortBtnOffset, Settings.SORT_DIRECTION,
                this.configSrc.getSetting(Settings.SORT_DIRECTION));
        this.buttonList.add(this.SortDirBox);
        sortBtnOffset += 20;

        this.searchBoxSettings = new GuiImgButton(itemAbsX - 18, sortBtnOffset, Settings.SEARCH_MODE,
                AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE));
        this.buttonList.add(this.searchBoxSettings);

        // ME閻椻晛鎼ч幖婊呭偍濡?
        this.itemSearchField = new MEGuiTextField(this.fontRenderer,
                itemAbsX + 3, itemAbsY + 4, 72, 12);
        this.itemSearchField.setEnableBackgroundDrawing(false);
        this.itemSearchField.setMaxStringLength(25);
        this.itemSearchField.setTextColor(0xFFFFFF);
        this.itemSearchField.setVisible(true);

        // SearchBoxMode 闁槒绶?
        final Enum searchModeSetting = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
        final boolean isJEIEnabled = SearchBoxMode.JEI_AUTOSEARCH == searchModeSetting
                || SearchBoxMode.JEI_MANUAL_SEARCH == searchModeSetting;

        if (isJEIEnabled && Platform.isJEIEnabled()) {
            memoryText = Integrations.jei().getSearchText();
        }

        if (!memoryText.isEmpty()) {
            this.itemSearchField.setText(memoryText);
            this.itemRepo.setSearchString(memoryText);
        }

        // 缁夊娅庨弮褏娈戦搹姘珯 ME 濡叉垝缍?
        this.guiSlots.removeIf(s -> s instanceof VirtualMEMonitorableSlot);

        // 閸掓稑缂?4x4 閾忔碍瀚?ME 濡叉垝缍?
        for (int row = 0; row < ITEM_PANEL_ROWS; row++) {
            for (int col = 0; col < ITEM_PANEL_COLS; col++) {
                final int slotIdx = col + row * ITEM_PANEL_COLS;
                final int slotX = itemRelX + ITEM_GRID_OFFSET_X + col * 18;
                final int slotY = itemRelY + ITEM_GRID_OFFSET_Y + row * 18;
                this.guiSlots.add(new VirtualMEMonitorableSlot(
                        slotIdx, slotX, slotY, this.itemRepo, slotIdx));
            }
        }

        // 鐠佸墽鐤哅E閻椻晛鎼ч棃銏℃緲濠婃艾濮╅弶鈥茬秴缂?
        this.itemPanelScrollbar.setLeft(itemRelX + ITEM_PANEL_WIDTH - 14)
                .setTop(itemRelY + ITEM_GRID_OFFSET_Y)
                .setHeight(ITEM_PANEL_ROWS * 18 - 2);
        this.updateItemPanelScrollbar();

        // ===== 婢跺嫮鎮婂Ο鈥崇础鏉堟挸鍤Σ鐣岀倳妞ゅ灚绮撮崝銊︽蒋閿涘牅缍呮禍搴ょ翻閸戠儤蝎閸欏厖鏅堕敍?=====
        this.updateProcessingScrollbar();

        this.itemRepo.setPower(true);

        // 闁氨鏁ら弮鐘靛殠缂佸牏顏崚鍥ㄥ床閹稿鎸?
        this.universalButtons = new UniversalTerminalButtons(
                ((appeng.container.AEBaseContainer) this.inventorySlots).getPlayerInv());
        this.universalButtons.initButtons(this.guiLeft, this.guiTop, this.buttonList, 500, this.itemRender);
    }

    // ========== JEI 閹烘帡娅庨崠鍝勭厵閿涘牓妲诲?JEI 闂堛垺婢橀柆顔藉皡娓氀囨桨閺夊尅绱?==========

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> area = new ArrayList<>();
        // 瀹革缚鏅堕幐澶愭尦閹烘帡娅庨崠鍝勭厵
        area.add(new Rectangle(this.guiLeft - 18, this.guiTop + 24 + 24, 18, 18));
        // 閸欏厖鏅堕弽閿嬫緲闂堛垺婢橀幒鎺楁珟閸栧搫鐓?
        final int panelScreenX = this.guiLeft + getPatternPanelX();
        final int panelScreenY = this.guiTop + getPatternPanelY();
        area.add(new Rectangle(panelScreenX, panelScreenY, PATTERN_PANEL_WIDTH, PATTERN_PANEL_TOTAL_HEIGHT));
        // 瀹革缚鏅禡E閻椻晛鎼ч棃銏℃緲閹烘帡娅庨崠鍝勭厵
        area.add(new Rectangle(getItemPanelAbsX(), getItemPanelAbsY(), ITEM_PANEL_WIDTH, ITEM_PANEL_HEIGHT));
        return area;
    }

    // ========== 閹稿鎸虫径鍕倞 ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        // 闁氨鏁ら弮鐘靛殠缂佸牏顏崚鍥ㄥ床閹稿鎸?
        if (this.universalButtons != null && this.universalButtons.handleButtonClick(btn)) {
            return;
        }

        final ContainerWirelessDualInterfaceTerminal ct = getDualContainer();

        // Crafting Status 閹稿鎸?
        if (btn == this.craftingStatusBtn) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_CRAFTING_STATUS));
            return;
        }

        // ===== 閹恒儱褰涚紒鍫㈩伂閻ㄥ嫰鐝禍顔藉瘻闁?=====
        if (guiButtonHashMap.containsKey(btn)) {
            BlockPos blockPos = blockPosHashMap.get(guiButtonHashMap.get(this.selectedButton));
            BlockPos blockPos2 = mc.player.getPosition();
            int playerDim = mc.world.provider.getDimension();
            int interfaceDim = dimHashMap.get(guiButtonHashMap.get(this.selectedButton));
            if (playerDim != interfaceDim) {
                try {
                    mc.player.sendStatusMessage(
                            PlayerMessages.InterfaceInOtherDimParam.get(interfaceDim,
                                    DimensionManager.getWorld(interfaceDim).provider.getDimensionType().getName()),
                            false);
                } catch (Exception e) {
                    mc.player.sendStatusMessage(PlayerMessages.InterfaceInOtherDim.get(), false);
                }
            } else {
                hilightBlock(blockPos,
                        System.currentTimeMillis() + 500 * BlockPosUtils.getDistance(blockPos, blockPos2), playerDim);
                mc.player.sendStatusMessage(
                        PlayerMessages.InterfaceHighlighted.get(blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                        false);
            }
            mc.player.closeScreen();
            return;
        }

        // ===== 閹恒儱褰涚紒鍫㈩伂閻ㄥ嫮鐐曢崐?閸戝繐宕愰幐澶愭尦 =====
        if (doubleButtonHashMap.containsKey(btn)) {
            final ClientDCInternalInv inv = doubleButtonHashMap.get(btn);
            final boolean backwards = Mouse.isButtonDown(1);
            int val = isShiftKeyDown() ? 1 : 0;
            if (backwards) {
                val |= 0b10;
            }
            try {
                NetworkHandler.instance().sendToServer(new PacketValueConfig(
                        "InterfaceTerminal.Double", val + "," + inv.getId()));
            } catch (final IOException e) {
                // ignore
            }
            return;
        }

        // ===== 閹恒儱褰涚紒鍫㈩伂閻ㄥ嫮鐡柅澶嬪瘻闁?=====
        if (btn == guiButtonHideFull) {
            onlyShowWithSpace = !onlyShowWithSpace;
            this.refreshList();
            return;
        }
        if (btn == guiButtonAssemblersOnly) {
            onlyMolecularAssemblers = !onlyMolecularAssemblers;
            this.refreshList();
            return;
        }
        if (btn == guiButtonBrokenRecipes) {
            onlyBrokenRecipes = !onlyBrokenRecipes;
            this.refreshList();
            return;
        }

        // ===== 缂佸牏顏弽宄扮础閹稿鎸?=====
        if (btn == this.terminalStyleBox) {
            final Enum<?> cv = terminalStyleBox.getCurrentValue();
            final boolean backwards = Mouse.isButtonDown(1);
            final Enum<?> next = appeng.util.EnumCycler.rotateEnumWildcard(cv, backwards,
                    terminalStyleBox.getSetting().getPossibleValues());
            AEConfig.instance().getConfigManager().putSetting(terminalStyleBox.getSetting(), next);
            terminalStyleBox.set(next);
            this.reinitialize();
            return;
        }

        // ===== ME 閻椻晛鎼ч棃銏℃緲閹烘帒绨?鏉╁洦鎶ら幐澶愭尦 =====
        if (btn instanceof GuiImgButton iBtn && iBtn.getSetting() != Settings.ACTIONS) {
            final boolean backwards = Mouse.isButtonDown(1);
            final Enum cv = iBtn.getCurrentValue();
            final Enum<?> next = appeng.util.EnumCycler.rotateEnumWildcard(cv, backwards,
                    iBtn.getSetting().getPossibleValues());

            if (btn == this.searchBoxSettings) {
                AEConfig.instance().getConfigManager().putSetting(iBtn.getSetting(), next);
            } else {
                try {
                    NetworkHandler.instance()
                            .sendToServer(new PacketValueConfig(iBtn.getSetting().name(), next.name()));
                } catch (final IOException e) {
                    AELog.debug(e);
                }
            }

            iBtn.set(next);

            if (next.getClass() == SearchBoxMode.class) {
                this.reinitialize();
            }
            return;
        }

        // ===== 閺嶉攱婢橀棃銏℃緲閹稿鎸?=====
        try {
            if (btn == this.tabCraftButton) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.CraftMode",
                        "0"));
            } else if (btn == this.tabProcessButton) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.CraftMode",
                        "1"));
            } else if (btn == this.encodeBtn) {
                final int value = (isCtrlKeyDown() ? 1 : 0) << 1 | (isShiftKeyDown() ? 1 : 0);
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.Encode", String.valueOf(value)));
                // Alt + 缂傛牜鐖滈敍姘辩椽閻礁鐣幋鎰倵閼奉亜濮╅弨鎯у弳閹恒儱褰?
                if (value == 0 && isAltKeyDown()) {
                    this.pendingPlacePattern = true;
                }
            } else if (btn == this.clearBtn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Clear", "1"));
            } else if (btn == this.substitutionsEnabledBtn || btn == this.substitutionsDisabledBtn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Substitute",
                        this.substitutionsEnabledBtn == btn ? "0" : "1"));
            } else if (btn == this.beSubstitutionsEnabledBtn || btn == this.beSubstitutionsDisabledBtn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.beSubstitute",
                        this.beSubstitutionsEnabledBtn == btn ? "0" : "1"));
            } else if (btn == this.invertBtn) {
                final boolean newInverted = !getDualContainer().isInverted();
                NetworkHandler.instance().sendToServer(
                        new PacketValueConfig("PatternTerminal.Invert", newInverted ? "1" : "0"));
            } else if (btn == this.combineEnabledBtn || btn == this.combineDisabledBtn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Combine",
                        this.combineEnabledBtn == btn ? "0" : "1"));
            } else if (btn == this.x2Btn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig(
                                isShiftKeyDown() ? "PatternTerminal.DivideByTwo" : "PatternTerminal.MultiplyByTwo",
                                "1"));
            } else if (btn == this.x3Btn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig(
                                isShiftKeyDown() ? "PatternTerminal.DivideByThree" : "PatternTerminal.MultiplyByThree",
                                "1"));
            } else if (btn == this.divTwoBtn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.DivideByTwo", "1"));
            } else if (btn == this.divThreeBtn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.DivideByThree", "1"));
            } else if (btn == this.plusOneBtn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig(
                                isShiftKeyDown() ? "PatternTerminal.DecreaseByOne" : "PatternTerminal.IncreaseByOne",
                                "1"));
            } else if (btn == this.minusOneBtn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.DecreaseByOne", "1"));
            } else if (btn == this.doubleBtn) {
                final boolean backwards = Mouse.isButtonDown(1);
                int val = isShiftKeyDown() ? 1 : 0;
                if (backwards) {
                    val |= 0b10;
                }
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.Double", String.valueOf(val)));
            }
        } catch (IOException e) {
            // ignore
        }
    }

    // ========== 闁款喚娲忔潏鎾冲弳婢跺嫮鎮?==========

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            // TAB 闁款喖顦╅悶?
            if (key == Keyboard.KEY_TAB) {
                this.searchFieldNames.setSuggestionToText();
            }
            if (character == '\t') {
                if (this.handleTab()) {
                    return;
                }
            }

            // 缁岀儤鐗告担婊€璐熺粭顑跨娑擃亜鐡х粭锔芥缁備焦顒涢敍鍫熷閺堝鎮崇槐銏☆攱閿?
            if (character == ' ') {
                if ((this.searchFieldInputs.getText().isEmpty() && this.searchFieldInputs.isFocused())
                        || (this.searchFieldOutputs.getText().isEmpty() && this.searchFieldOutputs.isFocused())
                        || (this.searchFieldNames.getText().isEmpty() && this.searchFieldNames.isFocused())
                        || (this.itemSearchField != null && this.itemSearchField.isFocused()
                                && this.itemSearchField.getText().isEmpty())) {
                    return;
                }
            }

            // ME 閹兼粎鍌ㄥ鍡楊槱閻炲棝鏁惄妯跨翻閸?
            if (this.itemSearchField != null && this.itemSearchField.isFocused()
                    && this.itemSearchField.textboxKeyTyped(character, key)) {
                final String searchText = this.itemSearchField.getText();
                this.itemRepo.setSearchString(searchText);
                this.itemRepo.updateView();
                this.updateItemPanelScrollbar();
                final Enum searchModeSetting = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
                final boolean isJEISync = SearchBoxMode.JEI_AUTOSEARCH == searchModeSetting
                        || SearchBoxMode.JEI_MANUAL_SEARCH == searchModeSetting;
                if (isJEISync && Platform.isJEIEnabled()) {
                    Integrations.jei().setSearchText(searchText);
                }
                return;
            }

            // 閹恒儱褰涚紒鍫㈩伂閹兼粎鍌ㄥ鍡楊槱閻炲棝鏁惄妯跨翻閸?
            if (this.searchFieldInputs.textboxKeyTyped(character, key)
                    || this.searchFieldOutputs.textboxKeyTyped(character, key)
                    || this.searchFieldNames.textboxKeyTyped(character, key)) {
                this.refreshList();
            } else {
                super.keyTyped(character, key);
            }
        }
    }

    /**
     * TAB 閻掞妇鍋ｉ崚鍥ㄥ床闁槒绶敍灞界殺 ME 閻椻晛鎼ч幖婊呭偍濡楀棗濮為崗銉ユ儕閻滎垬鈧?
     * 閻掞妇鍋ｅ顏嗗箚妞ゅ搫绨敍娆糿puts 閳?Outputs 閳?Names 閳?ME閻椻晛鎼ч幖婊呭偍 閳?Inputs...
     * Shift 閸欏秴鎮滈敍娆糿puts 閳?ME閻椻晛鎼ч幖婊呭偍 閳?Names 閳?Outputs 閳?Inputs...
     */
    private boolean handleTab() {
        if (this.itemSearchField != null && this.itemSearchField.isFocused()) {
            this.itemSearchField.setFocused(false);
            if (isShiftKeyDown()) {
                this.searchFieldNames.setFocused(true);
            } else {
                this.searchFieldInputs.setFocused(true);
            }
            return true;
        }
        if (searchFieldInputs.isFocused()) {
            searchFieldInputs.setFocused(false);
            if (isShiftKeyDown()) {
                if (this.itemSearchField != null) {
                    this.itemSearchField.setFocused(true);
                } else {
                    searchFieldNames.setFocused(true);
                }
            } else {
                searchFieldOutputs.setFocused(true);
            }
            return true;
        }
        if (searchFieldOutputs.isFocused()) {
            searchFieldOutputs.setFocused(false);
            if (isShiftKeyDown()) {
                searchFieldInputs.setFocused(true);
            } else {
                searchFieldNames.setFocused(true);
            }
            return true;
        }
        if (searchFieldNames.isFocused()) {
            searchFieldNames.setFocused(false);
            if (isShiftKeyDown()) {
                searchFieldOutputs.setFocused(true);
            } else if (this.itemSearchField != null) {
                this.itemSearchField.setFocused(true);
            } else {
                searchFieldInputs.setFocused(true);
            }
            return true;
        }
        return false;
    }

    // ========== 濠婃俺鐤嗘禍瀣╂婢跺嫮鎮?==========

    @Override
    protected void mouseWheelEvent(final int x, final int y, final int wheel) {
        // 濡偓閺屻儵绱堕弽鍥ㄦЦ閸氾箑婀锔挎櫠ME閻椻晛鎼ч棃銏℃緲閸栧搫鐓欓崘?
        final int panelX = getItemPanelAbsX();
        final int panelY = getItemPanelAbsY();

        if (x >= panelX && x < panelX + ITEM_PANEL_WIDTH
                && y >= panelY && y < panelY + ITEM_PANEL_HEIGHT) {
            this.itemPanelScrollbar.wheel(wheel);
            this.itemRepo.updateView();
            return;
        }

        // 濡偓閺屻儵绱堕弽鍥ㄦЦ閸氾箑婀崣鍏呮櫠閺嶉攱婢橀棃銏℃緲閸栧搫鐓欓崘鍜冪礄婢跺嫮鎮婂Ο鈥崇础娑撳鐐曟い鍨泊閸旑煉绱?
        if (!getDualContainer().isCraftingMode()) {
            final int patPanelAbsX = this.guiLeft + getPatternPanelX();
            final int patPanelAbsY = this.guiTop + getPatternPanelY();
            if (x >= patPanelAbsX && x < patPanelAbsX + PATTERN_PANEL_WIDTH
                    && y >= patPanelAbsY && y < patPanelAbsY + PATTERN_PANEL_UPPER_HEIGHT) {
                if (this.isMouseOverProcessingInputArea(x, y) && this.getTotalProcessingInputPages() > 1) {
                    this.updateProcessingInputScrollbar();
                    final int oldScroll = this.processingInputScrollbar.getCurrentScroll();
                    this.processingInputScrollbar.wheel(wheel);
                    if (oldScroll != this.processingInputScrollbar.getCurrentScroll()) {
                        this.setProcessingInputPage(this.processingInputScrollbar.getCurrentScroll());
                        return;
                    }
                }

                if (this.isMouseOverProcessingOutputArea(x, y) && getDualContainer().getTotalPages() > 1) {
                    this.processingScrollBar.wheel(wheel);
                    this.sendActivePageUpdate();
                    return;
                }
            }
        }

        // 閸忔湹绮崠鍝勭厵娴溿倗鏁遍悥鍓佽婢跺嫮鎮婇敍鍫熷复閸欙絿绮撶粩顖涚泊閸斻劍娼敍?
        super.mouseWheelEvent(x, y, wheel);
    }

    // ========== GUI 閸忔娊妫?==========

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
        if (this.itemSearchField != null) {
            memoryText = this.itemSearchField.getText();
            final Enum searchModeSetting = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
            final boolean isJEISync = SearchBoxMode.JEI_AUTOSEARCH == searchModeSetting
                    || SearchBoxMode.JEI_MANUAL_SEARCH == searchModeSetting;
            if (isJEISync && Platform.isJEIEnabled()) {
                Integrations.jei().setSearchText(memoryText);
            }
        }
    }

    // ========== Tick 閺囧瓨鏌婇敍鍦acePattern 閼奉亜濮╅弨鍓х枂闁槒绶敍?==========

    @Override
    public void updateScreen() {
        super.updateScreen();
        this.updateProcessingInputScrollbar();
        this.repositionSlots();
        this.updateProcessingScrollbar();

        // PlacePattern: 缂傛牜鐖滅€瑰本鍨氶崥搴ゅ殰閸斻劌鐨㈤弽閿嬫緲閺€鎯у弳妤傛ü瀵掗幒銉ュ經閻ㄥ嫮鈹栭梻鍙壭担?
        if (this.pendingPlacePattern) {
            this.pendingPlacePattern = false;
            final ContainerWirelessDualInterfaceTerminal ct = getDualContainer();
            if (ct.getPatternSlotOUT() != null && ct.getPatternSlotOUT().getHasStack()) {
                this.tryPlacePatternToHighlightedInterface();
            }
        }
    }

    /**
     * 鐏忔繆鐦亸鍡欑椽閻浇绶崙铏规畱閺嶉攱婢橀弨鎯у弳瑜版挸澧犻崣顖濐潌閹恒儱褰涢崚妤勩€冩稉顓狀儑娑撯偓娑擃亝婀佺粚娲＝濡叉垝缍呴惃鍕复閸欙絻鈧?
     * 闁秴宸昏ぐ鎾冲閸欘垵顫嗛惃?lines 閸掓銆冮敍灞惧閸掓壆顑囨稉鈧稉?ClientDCInternalInv 閺堝鈹栧Σ鐣屾畱閺夛紕娲伴妴?
     */
    private void tryPlacePatternToHighlightedInterface() {
        for (final ClientDCInternalInv inv : this.byId.values()) {
            final int slotLimit = inv.getInventory().getSlots();
            final int extraLines = numUpgradesMap.getOrDefault(inv, 0);
            final int maxSlots = Math.min(slotLimit, 9 * (1 + extraLines));

            for (int i = 0; i < maxSlots; i++) {
                if (inv.getInventory().getStackInSlot(i).isEmpty()) {
                    // 閹垫儳鍩岀粚娲＝濡叉垝缍呴敍灞藉絺闁?PlacePattern 鐠囬攱鐪?
                    try {
                        NetworkHandler.instance().sendToServer(new PacketValueConfig(
                                "PatternTerminal.PlacePattern",
                                inv.getId() + "," + i));
                    } catch (IOException e) {
                        // ignore
                    }
                    return;
                }
            }
        }
    }

    /**
     * 閸欐垿鈧?ActivePage 閺囧瓨鏌婇崚鐗堟箛閸旓紕顏?
     */
    private void sendActivePageUpdate() {
        final int newPage = this.processingScrollBar.getCurrentScroll();
        getDualContainer().setActivePage(newPage);
        try {
            NetworkHandler.instance().sendToServer(
                    new PacketValueConfig("PatternTerminal.ActivePage", String.valueOf(newPage)));
        } catch (IOException e) {
            // ignore
        }
    }

    private int getProcessingGridOffsetX() {
        return getDualContainer().isInverted() ? PROCESSING_INVERTED_GRID_OFFSET_X : PROCESSING_GRID_OFFSET_X;
    }

    private int getProcessingOutputOffsetX() {
        return getDualContainer().isInverted() ? PROCESSING_OUTPUT_INVERTED_OFFSET_X : PROCESSING_OUTPUT_NORMAL_OFFSET_X;
    }

    private void updateProcessingScrollbar() {
        final int panelRelX = getPatternPanelX();
        final int panelRelY = getPatternPanelY();
        this.processingScrollBar.setLeft(panelRelX + this.getProcessingOutputOffsetX() + PROCESSING_OUTPUT_COLUMNS * 18)
                .setTop(panelRelY + PROCESSING_OUTPUT_OFFSET_Y)
                .setHeight(PROCESSING_OUTPUT_ROWS * 18 - 2);

        final ContainerWirelessDualInterfaceTerminal container = getDualContainer();
        final int totalPages = container.getTotalPages();
        this.processingScrollBar.setRange(0, Math.max(0, totalPages - 1), 1);
        this.processingScrollBar.setCurrentScroll(container.getActivePage());
    }

    private int getTotalProcessingInputPages() {
        return Math.max(1, (PatternHelper.PROCESSING_INPUT_LIMIT + PatternHelper.PROCESSING_INPUT_PAGE_SLOTS - 1)
                / PatternHelper.PROCESSING_INPUT_PAGE_SLOTS);
    }

    private void updateProcessingInputScrollbar() {
        final int panelRelX = getPatternPanelX();
        final int panelRelY = getPatternPanelY();
        this.processingInputPage = Math.min(this.processingInputPage, this.getTotalProcessingInputPages() - 1);
        this.processingInputScrollbar.setLeft(panelRelX + this.getProcessingGridOffsetX()
                + PROCESSING_INPUT_WIDTH * 18 + 4)
                .setTop(panelRelY + PROCESSING_GRID_OFFSET_Y)
                .setHeight(PROCESSING_INPUT_ROWS * 18 - 2);
        this.processingInputScrollbar.setRange(0, Math.max(0, this.getTotalProcessingInputPages() - 1), 1);
        this.processingInputScrollbar.setCurrentScroll(this.processingInputPage);
    }

    private boolean updateItemPanelScrollFromMouse(final int mouseX, final int mouseY) {
        final int oldScroll = this.itemPanelScrollbar.getCurrentScroll();
        this.itemPanelScrollbar.click(this, mouseX - this.guiLeft, mouseY - this.guiTop);
        if (oldScroll != this.itemPanelScrollbar.getCurrentScroll()) {
            this.itemRepo.updateView();
            return true;
        }
        return false;
    }

    private boolean updatePatternInputScrollFromMouse(final int mouseX, final int mouseY) {
        if (getDualContainer().isCraftingMode() || this.getTotalProcessingInputPages() <= 1) {
            return false;
        }

        this.updateProcessingInputScrollbar();
        final int oldScroll = this.processingInputScrollbar.getCurrentScroll();
        this.processingInputScrollbar.click(this, mouseX - this.guiLeft, mouseY - this.guiTop);
        if (oldScroll != this.processingInputScrollbar.getCurrentScroll()) {
            this.setProcessingInputPage(this.processingInputScrollbar.getCurrentScroll());
            return true;
        }
        return false;
    }

    private boolean updatePatternOutputScrollFromMouse(final int mouseX, final int mouseY) {
        if (getDualContainer().isCraftingMode() || getDualContainer().getTotalPages() <= 1) {
            return false;
        }

        final int oldScroll = this.processingScrollBar.getCurrentScroll();
        this.processingScrollBar.click(this, mouseX - this.guiLeft, mouseY - this.guiTop);
        if (oldScroll != this.processingScrollBar.getCurrentScroll()) {
            this.sendActivePageUpdate();
            return true;
        }
        return false;
    }

    private void setProcessingInputPage(final int page) {
        this.processingInputPage = Math.max(0, Math.min(page, this.getTotalProcessingInputPages() - 1));
        this.repositionSlots();
    }

    private boolean isMouseOverProcessingInputArea(final int mouseX, final int mouseY) {
        final int panelAbsX = this.guiLeft + getPatternPanelX();
        final int panelAbsY = this.guiTop + getPatternPanelY();
        final int left = panelAbsX + this.getProcessingGridOffsetX();
        final int top = panelAbsY + PROCESSING_GRID_OFFSET_Y;
        final int right = left + PROCESSING_INPUT_WIDTH * 18 + 4 + this.processingInputScrollbar.getWidth();
        final int bottom = top + PROCESSING_INPUT_ROWS * 18;
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private boolean isMouseOverProcessingOutputArea(final int mouseX, final int mouseY) {
        final int panelAbsX = this.guiLeft + getPatternPanelX();
        final int panelAbsY = this.guiTop + getPatternPanelY();
        final int left = panelAbsX + this.getProcessingOutputOffsetX();
        final int top = panelAbsY + PROCESSING_OUTPUT_OFFSET_Y;
        final int right = left + PROCESSING_OUTPUT_COLUMNS * 18;
        final int bottom = top + PROCESSING_OUTPUT_ROWS * 18;
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    // ========== 缂佹ê鍩楅懗灞炬珯 ==========

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // ===== 缂佹ê鍩楅幒銉ュ經缂佸牏顏稉璁崇秼閼冲本娅欓敍鍫滃▏閻?208px 鐎硅棄瀹抽惃鍕垱閸ユ拝绱?=====
        this.bindTexture("guis/newinterfaceterminal.png");

        // 妞ゅ爼鍎?
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, MAIN_GUI_WIDTH, 53);

        // 閹恒儱褰涢崚妤勩€冪悰?
        for (int x = 0; x < this.rows; x++) {
            this.drawTexturedModalRect(offsetX, offsetY + 53 + x * 18, 0, 52, MAIN_GUI_WIDTH, 18);
        }

        // 閹恒儱褰涢崚妤勩€冩稉顓犳畱濡叉垝缍呴懗灞炬珯
        int offset = 51;
        final int ex = this.getScrollBar().getCurrentScroll();
        int linesDraw = 0;
        for (int x = 0; x < this.rows && linesDraw < rows && ex + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(ex + x);
            if (lineObj instanceof ClientDCInternalInv inv) {
                GlStateManager.color(1, 1, 1, 1);

                final int extraLines = numUpgradesMap.get(lineObj);
                final int slotLimit = inv.getInventory().getSlots();

                for (int row = 0; row < 1 + extraLines && linesDraw < rows; ++row) {
                    final int baseSlot = row * 9;
                    final int actualSlots = Math.min(9, slotLimit - baseSlot);

                    if (actualSlots > 0) {
                        final int actualWidth = actualSlots * 18;
                        this.drawTexturedModalRect(offsetX + 20, offsetY + offset, 20, 173, actualWidth, 18);
                    }

                    offset += 18;
                    linesDraw++;
                }
            } else {
                offset += 18;
                linesDraw++;
            }
        }

        // 鎼存洟鍎撮敍鍫㈠负鐎规儼鍎楅崠鍛隘閸╃噦绱?
        this.drawTexturedModalRect(offsetX, offsetY + 50 + this.rows * 18, 0, 158, MAIN_GUI_WIDTH, 99);

        // 閹恒儱褰涚紒鍫㈩伂閹兼粎鍌ㄥ?
        this.searchFieldInputs.drawTextBox();
        this.searchFieldOutputs.drawTextBox();
        this.searchFieldNames.drawTextBox();

        // ===== 缂佹ê鍩楅弮鐘靛殠缂佸牏顏崡鍥╅獓濡插€熷剹閺?=====
        this.bindTexture("guis/wirelessupgrades.png");
        Gui.drawModalRectWithCustomSizedTexture(offsetX + 198, offsetY + 127, 0, 0, 32, 32, 32, 32);

        // ===== 缂佹ê鍩楅崣鍏呮櫠閺嶉攱婢樼紓鏍у晸闂堛垺婢橀懗灞炬珯 =====
        drawPatternPanelBG(offsetX, offsetY);

        // ===== 缂佹ê鍩楀锔挎櫠ME閻椻晛鎼ч棃銏℃緲閼冲本娅?=====
        drawItemPanelBG(offsetX, offsetY);

        // 缂佹ê鍩楀锔挎櫠ME閻椻晛鎼ч棃銏℃緲濠婃艾濮╅弶?
        GlStateManager.pushMatrix();
        GlStateManager.translate(offsetX, offsetY, 0);
        this.itemPanelScrollbar.draw(this);
        // 婢跺嫮鎮婂Ο鈥崇础娑撳绮崚鎯扮翻閸戠儤蝎缂堝銆夊姘З閺?
        if (!getDualContainer().isCraftingMode()) {
            if (this.getTotalProcessingInputPages() > 1) {
                this.processingInputScrollbar.draw(this);
            }
            if (getDualContainer().getTotalPages() > 1) {
                this.processingScrollBar.draw(this);
            }
        }
        GlStateManager.popMatrix();

        // ME閻椻晛鎼ч幖婊呭偍濡?
        if (this.itemSearchField != null) {
            this.itemSearchField.drawTextBox();
        }
    }

    /**
     * 缂佹ê鍩楅崣鍏呮櫠閺嶉攱婢樼紓鏍у晸闂堛垺婢橀惃鍕剹閺咁垽绱欐担璺ㄦ暏 AE2Things 閻?pattern.png/pattern3.png 鐠愭潙娴橀敍?
     */
    private void drawPatternPanelBG(int offsetX, int offsetY) {
        final int panelX = offsetX + getPatternPanelX();
        final int panelY = offsetY + getPatternPanelY();
        final ContainerWirelessDualInterfaceTerminal ct = getDualContainer();

        GlStateManager.color(1, 1, 1, 1);

        // 娑撳﹤宕愰柈銊ュ瀻閿涙艾鎮庨幋鎰佸蹇庡▏閻?pattern3.png閿涘苯顦╅悶鍡樐佸蹇庡▏閻?pattern.png
        if (ct.isCraftingMode()) {
            this.mc.getTextureManager().bindTexture(PATTERN3_TEXTURE);
            this.drawTexturedModalRect(panelX, panelY, 0, 0,
                    PATTERN_PANEL_WIDTH, PATTERN_PANEL_UPPER_HEIGHT);
        } else if (ct.isInverted()) {
            this.mc.getTextureManager().bindTexture(PATTERN_TEXTURE);
            this.drawTexturedModalRect(panelX, panelY, 0, 0,
                    PATTERN_PANEL_WIDTH, PATTERN_PANEL_UPPER_HEIGHT);
        } else {
            this.mc.getTextureManager().bindTexture(PATTERN_TEXTURE);
            this.drawTexturedModalRect(panelX, panelY, 0, PATTERN_PANEL_UPPER_HEIGHT,
                    PATTERN_PANEL_WIDTH, PATTERN_PANEL_UPPER_HEIGHT);
        }

        // 娑撳宕愰柈銊ュ瀻閿涘牊鐗遍弶?IN/OUT 濡茶棄灏崺鐕傜礆
        this.mc.getTextureManager().bindTexture(PATTERN_TEXTURE);
        this.drawTexturedModalRect(panelX, panelY + PATTERN_PANEL_UPPER_HEIGHT,
                133, 0, PATTERN_PANEL_LOWER_WIDTH, PATTERN_PANEL_LOWER_HEIGHT);
        this.drawTexturedModalRect(panelX, panelY + PATTERN_PANEL_HEIGHT,
                173, 0, PATTERN_PANEL_FOOTER_WIDTH, PATTERN_PANEL_FOOTER_HEIGHT);
    }

    /**
     * 缂佹ê鍩楀锔挎櫠ME閻椻晛鎼ч棃銏℃緲閻ㄥ嫯鍎楅弲顖ょ礄瀹革缚绗呯憴鎺戭嚠姒绘劧绱?
     */
    private void drawItemPanelBG(int offsetX, int offsetY) {
        final int panelX = getItemPanelAbsX();
        final int panelY = getItemPanelAbsY();

        GlStateManager.color(1, 1, 1, 1);
        this.mc.getTextureManager().bindTexture(ITEMS_TEXTURE);
        this.drawTexturedModalRect(panelX, panelY, 0, 0, ITEM_PANEL_WIDTH, ITEM_PANEL_HEIGHT);
    }

    // ========== 缂佹ê鍩楅崜宥嗘珯 ==========

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        // ===== 閹恒儱褰涚紒鍫㈩伂閸撳秵娅欓敍鍫熺垼妫版ê鎷伴崠褰掑帳妤傛ü瀵掗敍?=====
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.WirelessTerminal.getLocal()),
                OFFSET_X + 2, 6, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), OFFSET_X + 2, this.ySize - 96, 4210752);

        final int currentScroll = this.getScrollBar().getCurrentScroll();

        int offset = 51;
        int linesDraw = 0;
        for (int x = 0; x < rows && linesDraw < rows && currentScroll + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(currentScroll + x);
            if (lineObj instanceof ClientDCInternalInv inv) {

                final int extraLines = numUpgradesMap.get(inv);
                final int totalSlots = inv.getInventory().getSlots();

                for (int row = 0; row < 1 + extraLines && linesDraw < rows; ++row) {
                    final int baseSlot = row * 9;

                    for (int z = 0; z < 9; z++) {
                        final int slotIndex = baseSlot + z;

                        if (slotIndex < totalSlots) {
                            final ItemStack stack = inv.getInventory().getStackInSlot(slotIndex);
                            if (this.matchedStacks.contains(stack)) {
                                drawRect(z * 18 + 22, 1 + offset, z * 18 + 22 + 16, 1 + offset + 16, 0x2A00FF00);
                            }
                        }
                    }
                    linesDraw++;
                    offset += 18;
                }
            } else if (lineObj instanceof String name) {
                final int nameRows = this.byName.get(name).size();
                if (nameRows > 1) {
                    name = name + " (" + nameRows + ')';
                }

                while (name.length() > 2 && this.fontRenderer.getStringWidth(name) > 158) {
                    name = name.substring(0, name.length() - 1);
                }
                this.fontRenderer.drawString(name, OFFSET_X + 3, 6 + offset, 4210752);
                linesDraw++;
                offset += 18;
            }
        }

        // ===== 閺嶉攱婢橀棃銏℃緲閸撳秵娅欓敍鍫熺垼妫版ê鎷伴幐澶愭尦閸欘垵顫嗛幀褝绱?=====
        final ContainerWirelessDualInterfaceTerminal ct = getDualContainer();
        final int panelX = getPatternPanelX();
        final int panelY = getPatternPanelY();

        this.fontRenderer.drawString(GuiText.PatternEncoding.getLocal(), panelX + 4,
                panelY + 4, 4210752);

        // 閹稿鎸抽崣顖濐潌閹団偓鏄忕帆
        if (ct.isCraftingMode()) {
            this.tabCraftButton.visible = true;
            this.tabProcessButton.visible = false;
            this.substitutionsEnabledBtn.visible = ct.isSubstitute();
            this.substitutionsDisabledBtn.visible = !ct.isSubstitute();
            this.beSubstitutionsEnabledBtn.visible = ct.isBeSubstitute();
            this.beSubstitutionsDisabledBtn.visible = !ct.isBeSubstitute();
            this.invertBtn.visible = false;
            this.combineEnabledBtn.visible = false;
            this.combineDisabledBtn.visible = false;
            this.x2Btn.visible = false;
            this.x3Btn.visible = false;
            this.divTwoBtn.visible = false;
            this.divThreeBtn.visible = false;
            this.plusOneBtn.visible = false;
            this.minusOneBtn.visible = false;
            this.doubleBtn.visible = false;
        } else {
            this.tabCraftButton.visible = false;
            this.tabProcessButton.visible = true;
            this.substitutionsEnabledBtn.visible = false;
            this.substitutionsDisabledBtn.visible = false;
            this.beSubstitutionsEnabledBtn.visible = false;
            this.beSubstitutionsDisabledBtn.visible = false;
            this.invertBtn.visible = true;
            this.combineEnabledBtn.visible = ct.isCombine();
            this.combineDisabledBtn.visible = !ct.isCombine();
            this.x2Btn.visible = true;
            this.x3Btn.visible = true;
            this.x2Btn.set(isShiftKeyDown() ? ActionItems.DIVIDE_BY_TWO : ActionItems.MULTIPLY_BY_TWO);
            this.x3Btn.set(isShiftKeyDown() ? ActionItems.DIVIDE_BY_THREE : ActionItems.MULTIPLY_BY_THREE);
            this.divTwoBtn.visible = false;
            this.divThreeBtn.visible = false;
            this.plusOneBtn.visible = true;
            this.plusOneBtn.set(isShiftKeyDown() ? ActionItems.DECREASE_BY_ONE : ActionItems.INCREASE_BY_ONE);
            this.minusOneBtn.visible = false;
            this.doubleBtn.visible = true;
        }
    }

    private void setButtonPos(final GuiButton button, final int x, final int y) {
        if (button == null) {
            return;
        }
        button.x = x;
        button.y = y;
    }

    private void updatePatternControlPositions() {
        final int panelScreenX = this.guiLeft + getPatternPanelX();
        final int panelScreenY = this.guiTop + getPatternPanelY();
        final ContainerWirelessDualInterfaceTerminal ct = getDualContainer();

        this.setButtonPos(this.encodeBtn, panelScreenX + 11, panelScreenY + 118);
        this.setButtonPos(this.tabCraftButton, panelScreenX + 39, panelScreenY + 93);
        this.setButtonPos(this.tabProcessButton, panelScreenX + 39, panelScreenY + 93);

        if (ct.isCraftingMode()) {
            // Keep crafting mode layout aligned with AE2Things pattern panel.
            this.setButtonPos(this.clearBtn, panelScreenX + 72, panelScreenY + 14);
            this.setButtonPos(this.substitutionsEnabledBtn, panelScreenX + 82, panelScreenY + 14);
            this.setButtonPos(this.substitutionsDisabledBtn, panelScreenX + 82, panelScreenY + 14);
            this.setButtonPos(this.beSubstitutionsEnabledBtn, panelScreenX + 82, panelScreenY + 24);
            this.setButtonPos(this.beSubstitutionsDisabledBtn, panelScreenX + 82, panelScreenY + 24);
            return;
        }

        final int offset = ct.isInverted() ? -3 * 18 : 0;
        this.setButtonPos(this.clearBtn, panelScreenX + 87 + offset, panelScreenY + 10);
        this.setButtonPos(this.substitutionsEnabledBtn, panelScreenX + 97 + offset, panelScreenY + 10);
        this.setButtonPos(this.substitutionsDisabledBtn, panelScreenX + 97 + offset, panelScreenY + 10);
        this.setButtonPos(this.beSubstitutionsEnabledBtn, panelScreenX + 97 + offset, panelScreenY + 69);
        this.setButtonPos(this.beSubstitutionsDisabledBtn, panelScreenX + 97 + offset, panelScreenY + 69);
        this.setButtonPos(this.invertBtn, panelScreenX + 87 + offset, panelScreenY + 20);
        this.setButtonPos(this.combineEnabledBtn, panelScreenX + 87 + offset, panelScreenY + 59);
        this.setButtonPos(this.combineDisabledBtn, panelScreenX + 87 + offset, panelScreenY + 59);

        final int adjBtnX1 = panelScreenX + PROCESSING_OUTPUT_OFFSET_X + 38 + offset;
        final int adjBtnX2 = panelScreenX + PROCESSING_OUTPUT_OFFSET_X + 28 + offset;
        this.setButtonPos(this.x3Btn, adjBtnX1, panelScreenY + 6);
        this.setButtonPos(this.x2Btn, adjBtnX1, panelScreenY + 16);
        this.setButtonPos(this.plusOneBtn, adjBtnX1, panelScreenY + 26);
        this.setButtonPos(this.divThreeBtn, adjBtnX2, panelScreenY + 6);
        this.setButtonPos(this.divTwoBtn, adjBtnX2, panelScreenY + 16);
        this.setButtonPos(this.minusOneBtn, adjBtnX2, panelScreenY + 26);
        this.setButtonPos(this.doubleBtn, adjBtnX2, panelScreenY + 36);
    }

    // ========== drawScreen ==========

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // ===== 閹恒儱褰涚紒鍫㈩伂 drawScreen 闁槒绶敍鍫濆З閹礁鍨卞鐑樺瘻闁筋喖鎷?SlotDisconnected閿?=====
        buttonList.clear();
        guiButtonHashMap.clear();
        doubleButtonHashMap.clear();
        inventorySlots.inventorySlots.removeIf(slot -> slot instanceof SlotDisconnected);

        guiButtonAssemblersOnly.set(
                onlyMolecularAssemblers ? ActionItems.MOLECULAR_ASSEMBLERS_ON : ActionItems.MOLECULAR_ASSEMBLERS_OFF);
        guiButtonHideFull.set(onlyShowWithSpace ? ActionItems.TOGGLE_SHOW_FULL_INTERFACES_OFF
                : ActionItems.TOGGLE_SHOW_FULL_INTERFACES_ON);
        guiButtonBrokenRecipes.set(onlyBrokenRecipes ? ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERNS_ON
                : ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERNS_OFF);
        terminalStyleBox.set(AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE));
        this.updatePatternControlPositions();

        buttonList.add(guiButtonAssemblersOnly);
        buttonList.add(guiButtonHideFull);
        buttonList.add(guiButtonBrokenRecipes);
        buttonList.add(terminalStyleBox);

        // 闁插秵鏌婂ǎ璇插娴滃苯鎮庢稉鈧紒鍫㈩伂娑撴挻婀侀幐澶愭尦
        if (this.craftingStatusBtn != null) {
            buttonList.add(this.craftingStatusBtn);
        }
        if (this.encodeBtn != null) {
            buttonList.add(this.encodeBtn);
        }
        if (this.clearBtn != null) {
            buttonList.add(this.clearBtn);
        }
        if (this.tabCraftButton != null) {
            buttonList.add(this.tabCraftButton);
        }
        if (this.tabProcessButton != null) {
            buttonList.add(this.tabProcessButton);
        }
        if (this.substitutionsEnabledBtn != null) {
            buttonList.add(this.substitutionsEnabledBtn);
        }
        if (this.substitutionsDisabledBtn != null) {
            buttonList.add(this.substitutionsDisabledBtn);
        }
        if (this.beSubstitutionsEnabledBtn != null) {
            buttonList.add(this.beSubstitutionsEnabledBtn);
        }
        if (this.beSubstitutionsDisabledBtn != null) {
            buttonList.add(this.beSubstitutionsDisabledBtn);
        }
        if (this.invertBtn != null) {
            buttonList.add(this.invertBtn);
        }
        if (this.combineEnabledBtn != null) {
            buttonList.add(this.combineEnabledBtn);
        }
        if (this.combineDisabledBtn != null) {
            buttonList.add(this.combineDisabledBtn);
        }
        if (this.x2Btn != null) {
            buttonList.add(this.x2Btn);
        }
        if (this.x3Btn != null) {
            buttonList.add(this.x3Btn);
        }
        if (this.plusOneBtn != null) {
            buttonList.add(this.plusOneBtn);
        }
        if (this.divTwoBtn != null) {
            buttonList.add(this.divTwoBtn);
        }
        if (this.divThreeBtn != null) {
            buttonList.add(this.divThreeBtn);
        }
        if (this.minusOneBtn != null) {
            buttonList.add(this.minusOneBtn);
        }
        if (this.doubleBtn != null) {
            buttonList.add(this.doubleBtn);
        }
        if (this.SortByBox != null) {
            buttonList.add(this.SortByBox);
        }
        if (this.SortDirBox != null) {
            buttonList.add(this.SortDirBox);
        }
        if (this.ViewBox != null) {
            buttonList.add(this.ViewBox);
        }
        if (this.searchBoxSettings != null) {
            buttonList.add(this.searchBoxSettings);
        }

        // 閸斻劍鈧胶鏁撻幋鎰复閸欙絽鍨悰銊ф畱閹稿鎸抽崪?SlotDisconnected
        int offset = 51;
        final int currentScroll = this.getScrollBar().getCurrentScroll();
        int linesDraw = 0;

        for (int x = 0; x < rows && linesDraw < rows && currentScroll + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(currentScroll + x);
            if (lineObj instanceof ClientDCInternalInv inv) {

                GuiButton guiButton = new GuiImgButton(guiLeft + 4, guiTop + offset + 1, Settings.ACTIONS,
                        ActionItems.HIGHLIGHT_INTERFACE);
                guiButtonHashMap.put(guiButton, inv);
                this.buttonList.add(guiButton);

                // 濮ｅ繋閲滈幒銉ュ經閻ㄥ嫮鐐曢崐?閸戝繐宕愰幐澶愭尦閿涘牆宕愮亸鍝勵嚟閿涘奔缍呮禍搴ㄧ彯娴滎喗瀵滈柦顔荤瑓閺傜櫢绱?
                GuiImgButton interfaceDoubleBtn = new GuiImgButton(guiLeft + 8, guiTop + offset + 10,
                        Settings.ACTIONS, ActionItems.DOUBLE_STACKS);
                interfaceDoubleBtn.setHalfSize(true);
                doubleButtonHashMap.put(interfaceDoubleBtn, inv);
                this.buttonList.add(interfaceDoubleBtn);

                final int extraLines = numUpgradesMap.get(inv);
                final int slotLimit = inv.getInventory().getSlots();

                for (int row = 0; row < 1 + extraLines && linesDraw < rows; ++row) {
                    final int baseSlot = row * 9;

                    for (int z = 0; z < 9; z++) {
                        final int slotIndex = baseSlot + z;
                        if (slotIndex < slotLimit) {
                            this.inventorySlots.inventorySlots.add(
                                    new SlotDisconnected(inv, slotIndex, z * 18 + 22, 1 + offset));
                        }
                    }
                    linesDraw++;
                    offset += 18;
                }

            } else if (lineObj instanceof String) {
                linesDraw++;
                offset += 18;
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        drawTooltip(searchFieldInputs, mouseX, mouseY);
        drawTooltip(searchFieldOutputs, mouseX, mouseY);
        drawTooltip(searchFieldNames, mouseX, mouseY);
    }

    // ========== 姒х姵鐖ｆ禍瀣╂婢跺嫮鎮?==========

    // ========== 娑擃參鏁悙鐟板毊閺嶉攱婢樺Σ鎴掔秴 閳?SET_PATTERN_VALUE ==========

    @Override
    protected void handleMouseClick(final Slot slot, final int slotIdx, final int mouseButton,
            final ClickType clickType) {
        // 娑擃參鏁悙鐟板毊閺嶉攱婢樼紓鏍垳濡叉垝缍呴敍鍦玪otFakeCraftingMatrix / OptionalSlotFake閿?
        if (clickType == ClickType.CLONE && slot instanceof SlotFake && slot.getHasStack()) {
            final IAEItemStack stack = AEItemStack.fromItemStack(slot.getStack());
            if (stack != null) {
                ((AEBaseContainer) this.inventorySlots).setTargetStack(stack);
                if (isCtrlKeyDown()) {
                    // Ctrl+娑擃參鏁?閳?閹垫挸绱戦崥宥囆炵拋鍓х枂閻ｅ矂娼?
                    final PacketInventoryAction p = new PacketInventoryAction(
                            InventoryAction.SET_PATTERN_NAME, slot.slotNumber, 0);
                    NetworkHandler.instance().sendToServer(p);
                } else {
                    // 娑擃參鏁?閳?閹垫挸绱戦弫鏉库偓鑹邦啎缂冾喚鏅棃?
                    final PacketInventoryAction p = new PacketInventoryAction(
                            InventoryAction.SET_PATTERN_VALUE, slot.slotNumber, 0);
                    NetworkHandler.instance().sendToServer(p);
                }
                return;
            }
        }
        super.handleMouseClick(slot, slotIdx, mouseButton, clickType);
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        // 娑擃參鏁幏鏍ㄥ闂堛垺婢?
        if (btn == 2) {
            for (PanelDragState dragState : getAllDragStates()) {
                if (dragState.isInDragArea(xCoord, yCoord)) {
                    dragState.startDrag(xCoord, yCoord);
                    return;
                }
            }
        }

        // ME閻椻晛鎼ч幖婊呭偍濡?
        if (this.itemSearchField != null) {
            this.itemSearchField.mouseClicked(xCoord, yCoord, btn);
            if (btn == 1 && this.isMouseOverSearchField(xCoord, yCoord)) {
                this.itemSearchField.setText("");
                this.itemRepo.setSearchString("");
                this.itemRepo.updateView();
                this.updateItemPanelScrollbar();
            }
        }

        // 閹恒儱褰涚紒鍫㈩伂閹兼粎鍌ㄥ?
        this.searchFieldInputs.mouseClicked(xCoord, yCoord, btn);
        this.searchFieldOutputs.mouseClicked(xCoord, yCoord, btn);
        this.searchFieldNames.mouseClicked(xCoord, yCoord, btn);

        if (btn == 0 && (this.updateItemPanelScrollFromMouse(xCoord, yCoord)
                || this.updatePatternInputScrollFromMouse(xCoord, yCoord)
                || this.updatePatternOutputScrollFromMouse(xCoord, yCoord))) {
            return;
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        for (PanelDragState state : getAllDragStates()) {
            if (state.isDragging()) {
                state.updateDrag(mouseX, mouseY);
                this.reinitialize();
                return;
            }
        }
        if (clickedMouseButton == 0 && (this.updateItemPanelScrollFromMouse(mouseX, mouseY)
                || this.updatePatternInputScrollFromMouse(mouseX, mouseY)
                || this.updatePatternOutputScrollFromMouse(mouseX, mouseY))) {
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        for (PanelDragState dragState : getAllDragStates()) {
            if (dragState.isDragging()) {
                dragState.endDrag();
                return;
            }
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    // ========== 閹恒儱褰涚紒鍫㈩伂閺佺増宓侀弴瀛樻煀閿涘牅绮?GuiInterfaceTerminal 缁夌粯顦查敍?==========

    /**
     * 閹恒儲鏁归弶銉ㄥ殰閺堝秴濮熺粩顖滄畱閹恒儱褰涚紒鍫㈩伂 NBT 閺囧瓨鏌?
     */
    public void postUpdate(final NBTTagCompound in) {
        if (in.getBoolean("clear")) {
            this.byId.clear();
            this.providerById.clear();
            this.refreshList = true;
        }

        for (final Object oKey : in.getKeySet()) {
            final String key = (String) oKey;
            if (key.startsWith("=")) {
                try {
                    final long id = Long.parseLong(key.substring(1), Character.MAX_RADIX);
                    final NBTTagCompound invData = in.getCompoundTag(key);
                    final boolean isProvider = invData.getBoolean("provider");

                    final ClientDCInternalInv current;
                    if (isProvider) {
                        int slotCount = invData.getInteger("slots");
                        current = this.getProviderById(id, invData.getLong("sortBy"), invData.getString("un"),
                                slotCount);
                    } else {
                        current = this.getById(id, invData.getLong("sortBy"), invData.getString("un"));
                    }

                    blockPosHashMap.put(current, NBTUtil.getPosFromTag(invData.getCompoundTag("pos")));
                    dimHashMap.put(current, invData.getInteger("dim"));

                    if (!isProvider) {
                        numUpgradesMap.put(current, invData.getInteger("numUpgrades"));
                    } else {
                        int tier = invData.getInteger("tier");
                        int slotCount = (int) Math.pow(2, 1 + Math.min(9, tier));
                        int lines = slotCount / 9;
                        numUpgradesMap.put(current, lines);
                    }

                    for (int x = 0; x < current.getInventory().getSlots(); x++) {
                        final String which = Integer.toString(x);
                        if (invData.hasKey(which)) {
                            current.getInventory().setStackInSlot(x, stackFromNBT(invData.getCompoundTag(which)));
                        }
                    }
                } catch (final NumberFormatException ignored) {
                }
            }
        }

        if (this.refreshList) {
            this.refreshList = false;
            this.cachedSearches.clear();
            this.refreshList();
        }
    }

    /**
     * 閸掗攱鏌婇幒銉ュ經閸掓銆冮敍鍫滅矤 GuiInterfaceTerminal 缁夌粯顦查敍?
     */
    private void refreshList() {
        this.byName.clear();
        this.buttonList.clear();
        this.matchedStacks.clear();

        final String searchFieldInputs = this.searchFieldInputs.getText().toLowerCase();
        final String searchFieldOutputs = this.searchFieldOutputs.getText().toLowerCase();
        final String searchFieldNames = this.searchFieldNames.getText().toLowerCase();

        final Set<Object> cachedSearch = this
                .getCacheForSearchTerm("IN:" + searchFieldInputs + " OUT:" + searchFieldOutputs
                        + "NAME:" + searchFieldNames + onlyShowWithSpace + onlyMolecularAssemblers + onlyBrokenRecipes);
        final boolean rebuild = cachedSearch.isEmpty();

        for (final ClientDCInternalInv entry : this.byId.values()) {
            if (!rebuild && !cachedSearch.contains(entry)) {
                continue;
            }

            boolean found = searchFieldInputs.isEmpty() && searchFieldOutputs.isEmpty();
            boolean interfaceHasFreeSlots = false;
            boolean interfaceHasBrokenRecipes = false;

            if (!found || onlyShowWithSpace || onlyBrokenRecipes) {
                int slot = 0;
                for (final ItemStack itemStack : entry.getInventory()) {
                    if (slot > 8 + numUpgradesMap.get(entry) * 9) {
                        break;
                    }

                    if (itemStack.isEmpty()) {
                        interfaceHasFreeSlots = true;
                    }

                    if (onlyBrokenRecipes && recipeIsBroken(itemStack)) {
                        interfaceHasBrokenRecipes = true;
                    }

                    if ((!searchFieldInputs.isEmpty()
                            && itemStackMatchesSearchTerm(itemStack, searchFieldInputs, 0))
                            || (!searchFieldOutputs.isEmpty()
                                    && itemStackMatchesSearchTerm(itemStack, searchFieldOutputs, 1))) {
                        found = true;
                        matchedStacks.add(itemStack);
                    }

                    slot++;
                }
            }

            if (!found) {
                cachedSearch.remove(entry);
                continue;
            }
            if (!entry.getName().toLowerCase().contains(searchFieldNames)) {
                cachedSearch.remove(entry);
                continue;
            }
            if (onlyMolecularAssemblers && !entry.getUnlocalizedName().equals(MOLECULAR_ASSEMBLER)) {
                cachedSearch.remove(entry);
                continue;
            }
            if (onlyShowWithSpace && !interfaceHasFreeSlots) {
                cachedSearch.remove(entry);
                continue;
            }
            if (onlyBrokenRecipes && !interfaceHasBrokenRecipes) {
                cachedSearch.remove(entry);
                continue;
            }

            this.byName.put(entry.getName(), entry);
            cachedSearch.add(entry);
        }

        for (final ClientDCInternalInv entry : this.providerById.values()) {
            if (!rebuild && !cachedSearch.contains(entry)) {
                continue;
            }

            boolean found = searchFieldInputs.isEmpty() && searchFieldOutputs.isEmpty();
            boolean interfaceHasFreeSlots = false;
            boolean interfaceHasBrokenRecipes = false;

            if (!found || onlyShowWithSpace || onlyBrokenRecipes) {
                int slot = 0;
                for (final ItemStack itemStack : entry.getInventory()) {
                    if (slot > 8 + numUpgradesMap.get(entry) * 9) {
                        break;
                    }

                    if (itemStack.isEmpty()) {
                        interfaceHasFreeSlots = true;
                    }

                    if (onlyBrokenRecipes && recipeIsBroken(itemStack)) {
                        interfaceHasBrokenRecipes = true;
                    }

                    if ((!searchFieldInputs.isEmpty()
                            && itemStackMatchesSearchTerm(itemStack, searchFieldInputs, 0))
                            || (!searchFieldOutputs.isEmpty()
                                    && itemStackMatchesSearchTerm(itemStack, searchFieldOutputs, 1))) {
                        found = true;
                        matchedStacks.add(itemStack);
                    }

                    slot++;
                }
            }

            if (!found) {
                cachedSearch.remove(entry);
                continue;
            }
            if (!entry.getName().toLowerCase().contains(searchFieldNames)) {
                cachedSearch.remove(entry);
                continue;
            }
            if (onlyMolecularAssemblers && !entry.getUnlocalizedName().equals(MOLECULAR_ASSEMBLER)) {
                cachedSearch.remove(entry);
                continue;
            }
            if (onlyShowWithSpace && !interfaceHasFreeSlots) {
                cachedSearch.remove(entry);
                continue;
            }
            if (onlyBrokenRecipes && !interfaceHasBrokenRecipes) {
                cachedSearch.remove(entry);
                continue;
            }

            this.byName.put(entry.getName(), entry);
            cachedSearch.add(entry);
        }

        this.names.clear();
        this.names.addAll(this.byName.keySet());
        Collections.sort(this.names);

        this.lines.clear();
        this.lines.ensureCapacity(this.names.size() + this.byId.size() + this.providerById.size());

        for (final String n : this.names) {
            this.lines.add(n);
            final ArrayList<ClientDCInternalInv> clientInventories = new ArrayList<>(this.byName.get(n));
            Collections.sort(clientInventories);
            this.lines.addAll(clientInventories);
        }

        this.setInterfaceScrollBar();
    }

    private boolean recipeIsBroken(final ItemStack stack) {
        if (stack == null) {
            return false;
        }
        if (stack.isEmpty()) {
            return false;
        }

        final NBTTagCompound encodedValue = stack.getTagCompound();
        if (encodedValue == null) {
            return true;
        }

        final World w = AppEng.proxy.getWorld();
        if (w == null) {
            return false;
        }

        try {
            new PatternHelper(stack, w);
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean itemStackMatchesSearchTerm(final ItemStack itemStack, final String searchTerm, int pass) {
        if (itemStack.isEmpty()) {
            return false;
        }

        final NBTTagCompound encodedValue = itemStack.getTagCompound();

        if (encodedValue == null) {
            return searchTerm.matches(GuiText.InvalidPattern.getLocal());
        }

        final NBTTagList tag;
        if (pass == 0) {
            tag = encodedValue.getTagList("in", Constants.NBT.TAG_COMPOUND);
        } else {
            tag = encodedValue.getTagList("out", Constants.NBT.TAG_COMPOUND);
        }

        boolean foundMatchingItemStack = false;
        final String[] splitTerm = searchTerm.split(" ");

        for (int i = 0; i < tag.tagCount(); i++) {
            final ItemStack parsedItemStack = new ItemStack(tag.getCompoundTagAt(i));
            if (!parsedItemStack.isEmpty()) {
                final String displayName = Platform
                        .getItemDisplayName(AEItemStackType.INSTANCE.getStorageChannel()
                                .createStack(parsedItemStack))
                        .toLowerCase();

                for (String term : splitTerm) {
                    if (term.length() > 1 && (term.startsWith("-") || term.startsWith("!"))) {
                        term = term.substring(1);
                        if (displayName.contains(term)) {
                            return false;
                        }
                    } else if (displayName.contains(term)) {
                        foundMatchingItemStack = true;
                    }
                }
            }
        }
        return foundMatchingItemStack;
    }

    private Set<Object> getCacheForSearchTerm(final String searchTerm) {
        if (!this.cachedSearches.containsKey(searchTerm)) {
            this.cachedSearches.put(searchTerm, new HashSet<>());
        }

        final Set<Object> cache = this.cachedSearches.get(searchTerm);

        if (cache.isEmpty() && searchTerm.length() > 1) {
            cache.addAll(this.getCacheForSearchTerm(searchTerm.substring(0, searchTerm.length() - 1)));
            return cache;
        }

        return cache;
    }

    private ClientDCInternalInv getById(final long id, final long sortBy, final String string) {
        ClientDCInternalInv o = this.byId.get(id);

        if (o == null) {
            this.byId.put(id,
                    o = new ClientDCInternalInv(DualityInterface.NUMBER_OF_PATTERN_SLOTS, id, sortBy, string));
            this.refreshList = true;
        }

        return o;
    }

    private ClientDCInternalInv getProviderById(final long id, final long sortBy, final String string,
            final int stackSize) {
        ClientDCInternalInv o = this.providerById.get(id);

        if (o == null) {
            this.providerById.put(id,
                    o = new ClientDCInternalInv(stackSize, id, sortBy, string));
            this.refreshList = true;
        }

        return o;
    }

    // ========== 鏉堝懎濮弬瑙勭《 ==========

    private boolean isMouseOverSearchField(int mouseX, int mouseY) {
        final int itemAbsX = getItemPanelAbsX();
        final int itemAbsY = getItemPanelAbsY();
        return mouseX >= itemAbsX + 3 && mouseX < itemAbsX + 3 + 72
                && mouseY >= itemAbsY + 4 && mouseY < itemAbsY + 4 + 12;
    }

    private void reinitialize() {
        this.buttonList.clear();
        this.initGui();
    }

    private void initDragStates() {
        // ME閻椻晛鎼ч棃銏℃緲閿涙碍瀚嬮幏钘夊隘閸╃喍璐熼幖婊呭偍濡楀棙澧嶉崷銊ф畱妞ゅ爼鍎撮崠鍝勭厵
        this.itemPanelDragState = new PanelDragState((mouseX, mouseY) -> {
            final int absX = getItemPanelAbsX();
            final int absY = getItemPanelAbsY();
            return mouseX >= absX && mouseX < absX + ITEM_PANEL_WIDTH
                    && mouseY >= absY && mouseY < absY + ITEM_GRID_OFFSET_Y;
        });

        // 閺嶉攱婢橀棃銏℃緲閿涙碍瀚嬮幏钘夊隘閸╃喍璐熸惔鏇㈠劥閹稿鎸抽崠鍝勭厵
        this.patternPanelDragState = new PanelDragState((mouseX, mouseY) -> {
            final int absX = this.guiLeft + getPatternPanelX();
            final int absY = this.guiTop + getPatternPanelY();
            return mouseX >= absX && mouseX < absX + PATTERN_PANEL_WIDTH
                    && mouseY >= absY + PATTERN_PANEL_HEIGHT
                    && mouseY < absY + PATTERN_PANEL_HEIGHT + 16;
        });
    }

    private List<PanelDragState> getAllDragStates() {
        return Arrays.asList(this.itemPanelDragState, this.patternPanelDragState);
    }

    // ========== ISortSource 閹恒儱褰涚€圭偟骞?==========

    @Override
    public Enum getSortBy() {
        return this.configSrc.getSetting(Settings.SORT_BY);
    }

    @Override
    public Enum getSortDir() {
        return this.configSrc.getSetting(Settings.SORT_DIRECTION);
    }

    @Override
    public Enum getSortDisplay() {
        return this.configSrc.getSetting(Settings.VIEW_MODE);
    }

    // ========== IConfigManagerHost 閹恒儱褰涚€圭偟骞?==========

    @Override
    public void updateSetting(final IConfigManager manager, final Enum<?> settingName, final Enum<?> newValue) {
        if (this.SortByBox != null) {
            this.SortByBox.set(this.configSrc.getSetting(Settings.SORT_BY));
        }
        if (this.SortDirBox != null) {
            this.SortDirBox.set(this.configSrc.getSetting(Settings.SORT_DIRECTION));
        }
        if (this.ViewBox != null) {
            this.ViewBox.set(this.configSrc.getSetting(Settings.VIEW_MODE));
        }
        this.itemRepo.updateView();
    }

    // ========== 婢舵牠鍎?API閿涙EI Recipe Transfer 鐠嬪啰鏁?==========

    /**
     * 鐠佸墽鐤?Names 閹兼粎鍌ㄥ鍡欐畱瀵ら缚顔呴弬鍥ㄦ拱閿涘牏浼嗛懝鍙夊絹缁€鐑樻瀮鐎涙绱氶妴?
     * 閻?RecipeTransferHandler 閸?JEI 婵夘偄鍙嗛柊宥嗘煙閸氬氦鐨熼悽銊ｂ偓?
     * 閻劍鍩涢幐?TAB 閸欘垰鐨㈠楦款唴閺傚洦婀扮涵顔款吇娑撶儤顒滃蹇旀偝缁便垺鏋冮張顑锯偓?
     *
     * @param suggestion 瀵ら缚顔呴弬鍥ㄦ拱閿涘牓鈧艾鐖堕弰顖炲帳閺傜绶崙铏瑰⒖閸濅胶娈戦弰鍓с仛閸氬秶袨閿?
     */
    public void setSearchFieldSuggestion(final String suggestion) {
        this.searchFieldNames.setSuggestion(suggestion);
    }

    /**
     * 閻╁瓨甯寸拋鍓х枂 Names 閹兼粎鍌ㄥ鍡欐畱閺傚洦婀伴妴?
     *
     * @param text 閹兼粎鍌ㄩ弬鍥ㄦ拱
     */
    public void setSearchFieldText(final String text) {
        this.searchFieldNames.setText(text);
    }

    // ========== 闂堛垺婢橀幏鏍ㄥ閻樿埖鈧礁鐨濈憗鍛 ==========

    /**
     * 鐏忎浇顥婇崡鏇氶嚋闂堛垺婢橀惃鍕珛閹风晫濮搁幀浣告嫲鐞涘奔璐熼妴?
     * 閸栧懏瀚幏鏍ㄥ閸嬪繒些闁插繈鈧焦瀚嬮幏鍊熺箖缁嬪鑵戦惃鍕崳婵娼楅弽鍥风礉娴犮儱寮烽幏鏍ㄥ閸栧搫鐓欏Λ鈧ù瀣偓鏄忕帆閵?
     */
    private static class PanelDragState {

        /** 瑜版挸澧犻幏鏍ㄥ閸嬪繒些闁?*/
        private int dragOffsetX = 0;
        private int dragOffsetY = 0;

        /** 閺勵垰鎯佸锝呮躬閹锋牗瀚?*/
        private boolean dragging = false;

        /** 閹锋牗瀚跨挧宄邦潗閺冨墎娈戞Η鐘崇垼閸ф劖鐖?*/
        private int dragStartMouseX;
        private int dragStartMouseY;

        /** 閹锋牗瀚跨挧宄邦潗閺冨墎娈戦崑蹇曅╅柌?*/
        private int dragStartOffsetX;
        private int dragStartOffsetY;

        /** 閹锋牗瀚块崠鍝勭厵濡偓濞村娅?*/
        private final BiPredicate<Integer, Integer> dragAreaChecker;

        PanelDragState(BiPredicate<Integer, Integer> dragAreaChecker) {
            this.dragAreaChecker = dragAreaChecker;
        }

        /**
         * 濡偓閺屻儲瀵氱€规艾娼楅弽鍥ㄦЦ閸氾箑婀幏鏍ㄥ閸栧搫鐓欓崘?
         */
        boolean isInDragArea(int mouseX, int mouseY) {
            return dragAreaChecker.test(mouseX, mouseY);
        }

        /**
         * 瀵偓婵瀚嬮幏?
         */
        void startDrag(int mouseX, int mouseY) {
            this.dragging = true;
            this.dragStartMouseX = mouseX;
            this.dragStartMouseY = mouseY;
            this.dragStartOffsetX = this.dragOffsetX;
            this.dragStartOffsetY = this.dragOffsetY;
        }

        /**
         * 閺囧瓨鏌婇幏鏍ㄥ閸嬪繒些
         */
        void updateDrag(int mouseX, int mouseY) {
            this.dragOffsetX = this.dragStartOffsetX + (mouseX - this.dragStartMouseX);
            this.dragOffsetY = this.dragStartOffsetY + (mouseY - this.dragStartMouseY);
        }

        /**
         * 缂佹挻娼幏鏍ㄥ
         */
        void endDrag() {
            this.dragging = false;
        }

        int getDragOffsetX() {
            return dragOffsetX;
        }

        int getDragOffsetY() {
            return dragOffsetY;
        }

        boolean isDragging() {
            return dragging;
        }
    }
}
