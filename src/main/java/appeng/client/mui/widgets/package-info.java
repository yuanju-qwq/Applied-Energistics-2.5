/**
 * MUI Widget 控件集合。
 * <p>
 * 所有 MUI 控件都实现 {@link appeng.client.mui.IMUIWidget} 接口，
 * 可以添加到 {@link appeng.client.mui.AEBasePanel} 中使用。
 *
 * <h3>控件映射（旧 Widget → MUI Widget）</h3>
 * <table>
 *   <tr><th>旧 Widget</th><th>MUI 替代</th></tr>
 *   <tr><td>GuiScrollbar</td><td>{@link appeng.client.mui.widgets.MUIScrollBar}</td></tr>
 *   <tr><td>GuiImgButton</td><td>{@link appeng.client.mui.widgets.MUIButtonWidget}</td></tr>
 *   <tr><td>GuiToggleButton</td><td>{@link appeng.client.mui.widgets.MUIToggleButton}</td></tr>
 *   <tr><td>GuiTabButton</td><td>{@link appeng.client.mui.widgets.MUITabContainer}</td></tr>
 *   <tr><td>GuiProgressBar</td><td>{@link appeng.client.mui.widgets.MUIProgressWidget}</td></tr>
 *   <tr><td>GuiNumberBox</td><td>{@link appeng.client.mui.widgets.MUINumberFieldWidget}</td></tr>
 *   <tr><td>MEGuiTextField</td><td>{@link appeng.client.mui.widgets.MUITextFieldWidget}</td></tr>
 *   <tr><td>GuiImgLabel</td><td>{@link appeng.client.mui.widgets.MUIDrawableWidget}</td></tr>
 *   <tr><td>GuiCustomSlot</td><td>{@link appeng.client.mui.widgets.MUISlotWidget}</td></tr>
 *   <tr><td>GuiFluidSlot / GuiFluidTank</td><td>{@link appeng.client.mui.widgets.MUIFluidSlotWidget}</td></tr>
 *   <tr><td>TypeToggleButton</td><td>{@link appeng.client.mui.widgets.MUICycleButtonWidget}</td></tr>
 *   <tr><td>UniversalTerminalButtons</td><td>{@link appeng.client.mui.widgets.MUITabGroup}</td></tr>
 * </table>
 */
package appeng.client.mui.widgets;
