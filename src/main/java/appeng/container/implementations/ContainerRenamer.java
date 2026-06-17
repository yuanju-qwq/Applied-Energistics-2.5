package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.SecurityPermissions;
import appeng.api.parts.IPart;
import appeng.client.mui.widgets.MUITextFieldWidget;
import appeng.container.AEBaseContainer;
import appeng.container.interfaces.ITextValueSink;
import appeng.helpers.ICustomNameObject;
import appeng.util.Platform;

public class ContainerRenamer extends AEBaseContainer {
    private final ICustomNameObject namedObject;

    @SideOnly(Side.CLIENT)
    private ITextValueSink textSink;

    public ContainerRenamer(InventoryPlayer ip, ICustomNameObject obj) {
        super(ip, obj instanceof TileEntity ? (TileEntity) obj : null, obj instanceof IPart ? (IPart) obj : null);
        namedObject = obj;
    }

    @SideOnly(Side.CLIENT)
    @Deprecated
    public void setTextField(final MUITextFieldWidget name) {
        this.setTextSink(name::setText);
    }

    @SideOnly(Side.CLIENT)
    public void setTextSink(final ITextValueSink textSink) {
        this.textSink = textSink;
        if (getCustomName() != null) {
            this.textSink.setText(getCustomName());
        }
    }

    public void setNewName(String newValue) {
        this.namedObject.setCustomName(newValue);

    }

    @Override
    public void setCustomName(final String customName) {
        super.setCustomName(customName);
        if (!Platform.isServer() && customName != null && this.textSink != null) {
            this.textSink.setText(customName);
        }
    }

    @Override
    public void detectAndSendChanges() {
        verifyPermissions(SecurityPermissions.BUILD, false);
        super.detectAndSendChanges();
        if (!Platform.isServer() && getCustomName() != null && this.textSink != null) {
            this.textSink.setText(getCustomName());
        }
    }
}
