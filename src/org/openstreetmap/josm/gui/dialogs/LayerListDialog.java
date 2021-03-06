// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.dialogs;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListSelectionModel;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.DuplicateLayerAction;
import org.openstreetmap.josm.actions.MergeLayerAction;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.io.SaveLayersDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.Layer.LayerAction;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.widgets.PopupMenuLauncher;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * This is a toggle dialog which displays the list of layers. Actions allow to
 * change the ordering of the layers, to hide/show layers, to activate layers,
 * and to delete layers.
 *
 */
public class LayerListDialog extends ToggleDialog {
    //static private final Logger logger = Logger.getLogger(LayerListDialog.class.getName());

    /** the unique instance of the dialog */
    static private LayerListDialog instance;

    /**
     * Creates the instance of the dialog. It's connected to the map frame <code>mapFrame</code>
     *
     * @param mapFrame the map frame
     */
    static public void createInstance(MapFrame mapFrame) {
        if (instance != null)
            throw new IllegalStateException("Dialog was already created");
        instance = new LayerListDialog(mapFrame);
    }

    /**
     * Replies the instance of the dialog
     *
     * @return the instance of the dialog
     * @throws IllegalStateException thrown, if the dialog is not created yet
     * @see #createInstance(MapFrame)
     */
    static public LayerListDialog getInstance() throws IllegalStateException {
        if (instance == null)
            throw new IllegalStateException("Dialog not created yet. Invoke createInstance() first");
        return instance;
    }

    /** the model for the layer list */
    private LayerListModel model;

    /** the selection model */
    private DefaultListSelectionModel selectionModel;

    /** the list of layers (technically its a JTable, but appears like a list) */
    private LayerList layerList;

    private SideButton opacityButton;

    ActivateLayerAction activateLayerAction;

    protected JPanel createButtonPanel() {
        JPanel buttonPanel = getButtonPanel(5);

        // -- move up action
        MoveUpAction moveUpAction = new MoveUpAction();
        adaptTo(moveUpAction, model);
        adaptTo(moveUpAction,selectionModel);
        buttonPanel.add(new SideButton(moveUpAction));

        // -- move down action
        MoveDownAction moveDownAction = new MoveDownAction();
        adaptTo(moveDownAction, model);
        adaptTo(moveDownAction,selectionModel);
        buttonPanel.add(new SideButton(moveDownAction));

        // -- activate action
        activateLayerAction = new ActivateLayerAction();
        adaptTo(activateLayerAction, selectionModel);
        buttonPanel.add(new SideButton(activateLayerAction));

        // -- show hide action
        ShowHideLayerAction showHideLayerAction = new ShowHideLayerAction();
        adaptTo(showHideLayerAction, selectionModel);
        buttonPanel.add(new SideButton(showHideLayerAction));

        //-- layer opacity action
        LayerOpacityAction layerOpacityAction = new LayerOpacityAction();
        adaptTo(layerOpacityAction, selectionModel);
        opacityButton = new SideButton(layerOpacityAction);
        buttonPanel.add(opacityButton);

        // -- merge layer action
        MergeAction mergeLayerAction = new MergeAction();
        adaptTo(mergeLayerAction, model);
        adaptTo(mergeLayerAction,selectionModel);
        buttonPanel.add(new SideButton(mergeLayerAction));

        // -- duplicate layer action
        DuplicateAction duplicateLayerAction = new DuplicateAction();
        adaptTo(duplicateLayerAction, model);
        adaptTo(duplicateLayerAction, selectionModel);
        buttonPanel.add(new SideButton(duplicateLayerAction));

        //-- delete layer action
        DeleteLayerAction deleteLayerAction = new DeleteLayerAction();
        layerList.getActionMap().put("deleteLayer", deleteLayerAction);
        adaptTo(deleteLayerAction, selectionModel);
        buttonPanel.add(new SideButton(deleteLayerAction, false));

        return buttonPanel;
    }

    /**
     * Create an layer list and attach it to the given mapView.
     */
    protected LayerListDialog(MapFrame mapFrame) {
        super(tr("Layers"), "layerlist", tr("Open a list of all loaded layers."),
                Shortcut.registerShortcut("subwindow:layers", tr("Toggle: {0}", tr("Layers")), KeyEvent.VK_L, Shortcut.GROUP_LAYER), 100, true);

        // create the models
        //
        selectionModel = new DefaultListSelectionModel();
        selectionModel.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        model = new LayerListModel(selectionModel);

        // create the list control
        //
        layerList = new LayerList(model);
        layerList.setSelectionModel(selectionModel);
        layerList.addMouseListener(new PopupMenuHandler());
        layerList.setBackground(UIManager.getColor("Button.background"));
        layerList.putClientProperty("terminateEditOnFocusLost", true);
        layerList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        layerList.setTableHeader(null);
        layerList.setShowGrid(false);
        layerList.setIntercellSpacing(new Dimension(0, 0));
        layerList.getColumnModel().getColumn(0).setCellRenderer(new ActiveLayerCellRenderer());
        layerList.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(new ActiveLayerCheckBox()));
        layerList.getColumnModel().getColumn(0).setMaxWidth(12);
        layerList.getColumnModel().getColumn(0).setPreferredWidth(12);
        layerList.getColumnModel().getColumn(0).setResizable(false);
        layerList.getColumnModel().getColumn(1).setCellRenderer(new LayerVisibleCellRenderer());
        layerList.getColumnModel().getColumn(1).setCellEditor(new LayerVisibleCellEditor(new LayerVisibleCheckBox()));
        layerList.getColumnModel().getColumn(1).setMaxWidth(16);
        layerList.getColumnModel().getColumn(1).setPreferredWidth(16);
        layerList.getColumnModel().getColumn(1).setResizable(false);
        layerList.getColumnModel().getColumn(2).setCellRenderer(new LayerNameCellRenderer());
        layerList.getColumnModel().getColumn(2).setCellEditor(new LayerNameCellEditor(new JTextField()));
        for (KeyStroke ks : new KeyStroke[] {
                KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.SHIFT_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.SHIFT_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.SHIFT_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.SHIFT_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0),
        })
        {
            layerList.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ks, new Object());
        }

        add(new JScrollPane(layerList), BorderLayout.CENTER);

        // init the model
        //
        final MapView mapView = mapFrame.mapView;
        model.populate();
        model.setSelectedLayer(mapView.getActiveLayer());
        model.addLayerListModelListener(
                new LayerListModelListener() {
                    @Override
                    public void makeVisible(int row, Layer layer) {
                        layerList.scrollToVisible(row, 0);
                        layerList.repaint();
                    }
                    @Override
                    public void refresh() {
                        layerList.repaint();
                    }
                }
        );

        add(createButtonPanel(), BorderLayout.SOUTH);
    }

    @Override
    public void showNotify() {
        MapView.addLayerChangeListener(activateLayerAction);
        MapView.addLayerChangeListener(model);
        model.populate();
    }

    @Override
    public void hideNotify() {
        MapView.removeLayerChangeListener(model);
        MapView.removeLayerChangeListener(activateLayerAction);
    }

    public LayerListModel getModel() {
        return model;
    }

    protected interface IEnabledStateUpdating {
        void updateEnabledState();
    }

    /**
     * Wires <code>listener</code> to <code>listSelectionModel</code> in such a way, that
     * <code>listener</code> receives a {@see IEnabledStateUpdating#updateEnabledState()}
     * on every {@see ListSelectionEvent}.
     *
     * @param listener  the listener
     * @param listSelectionModel  the source emitting {@see ListSelectionEvent}s
     */
    protected void adaptTo(final IEnabledStateUpdating listener, ListSelectionModel listSelectionModel) {
        listSelectionModel.addListSelectionListener(
                new ListSelectionListener() {
                    @Override
                    public void valueChanged(ListSelectionEvent e) {
                        listener.updateEnabledState();
                    }
                }
        );
    }

    /**
     * Wires <code>listener</code> to <code>listModel</code> in such a way, that
     * <code>listener</code> receives a {@see IEnabledStateUpdating#updateEnabledState()}
     * on every {@see ListDataEvent}.
     *
     * @param listener  the listener
     * @param listSelectionModel  the source emitting {@see ListDataEvent}s
     */
    protected void adaptTo(final IEnabledStateUpdating listener, LayerListModel listModel) {
        listModel.addTableModelListener(
                new TableModelListener() {

                    @Override
                    public void tableChanged(TableModelEvent e) {
                        listener.updateEnabledState();
                    }
                }
        );
    }

    @Override
    public void destroy() {
        super.destroy();
        instance = null;
    }

    /**
     * The action to delete the currently selected layer
     */
    public final class DeleteLayerAction extends AbstractAction implements IEnabledStateUpdating, LayerAction {
        /**
         * Creates a {@see DeleteLayerAction} which will delete the currently
         * selected layers in the layer dialog.
         *
         */
        public DeleteLayerAction() {
            putValue(SMALL_ICON,ImageProvider.get("dialogs", "delete"));
            putValue(SHORT_DESCRIPTION, tr("Delete the selected layers."));
            putValue(NAME, tr("Delete"));
            putValue("help", HelpUtil.ht("/Dialog/LayerList#DeleteLayer"));
            updateEnabledState();
        }

        protected boolean enforceUploadOrSaveModifiedData(List<Layer> selectedLayers) {
            SaveLayersDialog dialog = new SaveLayersDialog(Main.parent);
            List<OsmDataLayer> layersWithUnmodifiedChanges = new ArrayList<OsmDataLayer>();
            for (Layer l: selectedLayers) {
                if (! (l instanceof OsmDataLayer)) {
                    continue;
                }
                OsmDataLayer odl = (OsmDataLayer)l;
                if ((odl.requiresSaveToFile() || odl.requiresUploadToServer()) && odl.data.isModified()) {
                    layersWithUnmodifiedChanges.add(odl);
                }
            }
            dialog.prepareForSavingAndUpdatingLayersBeforeDelete();
            if (!layersWithUnmodifiedChanges.isEmpty()) {
                dialog.getModel().populate(layersWithUnmodifiedChanges);
                dialog.setVisible(true);
                switch(dialog.getUserAction()) {
                case CANCEL: return false;
                case PROCEED: return true;
                default: return false;
                }
            }
            return true;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            List<Layer> selectedLayers = getModel().getSelectedLayers();
            if (selectedLayers.isEmpty())
                return;
            if (! enforceUploadOrSaveModifiedData(selectedLayers))
                return;
            for(Layer l: selectedLayers) {
                Main.main.removeLayer(l);
            }
        }

        @Override
        public void updateEnabledState() {
            setEnabled(! getModel().getSelectedLayers().isEmpty());
        }

        @Override
        public Component createMenuComponent() {
            return new JMenuItem(this);
        }

        @Override
        public boolean supportLayers(List<Layer> layers) {
            return true;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof DeleteLayerAction;
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }
    }

    public final class ShowHideLayerAction extends AbstractAction implements IEnabledStateUpdating, LayerAction {
        private  Layer layer;

        /**
         * Creates a {@see ShowHideLayerAction} which toggle the visibility of
         * a specific layer.
         *
         * @param layer  the layer. Must not be null.
         * @exception IllegalArgumentException thrown, if layer is null
         */
        public ShowHideLayerAction(Layer layer) throws IllegalArgumentException {
            this();
            putValue(NAME, tr("Show/Hide"));
            CheckParameterUtil.ensureParameterNotNull(layer, "layer");
            this.layer = layer;
            updateEnabledState();
        }

        /**
         * Creates a {@see ShowHideLayerAction} which will toggle the visibility of
         * the currently selected layers
         *
         */
        public ShowHideLayerAction() {
            putValue(SMALL_ICON, ImageProvider.get("dialogs", "showhide"));
            putValue(SHORT_DESCRIPTION, tr("Toggle visible state of the selected layer."));
            putValue("help", HelpUtil.ht("/Dialog/LayerList#ShowHideLayer"));
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (layer != null) {
                layer.toggleVisible();
            } else {
                for(Layer l : model.getSelectedLayers()) {
                    l.toggleVisible();
                }
            }
        }

        @Override
        public void updateEnabledState() {
            if (layer == null) {
                setEnabled(! getModel().getSelectedLayers().isEmpty());
            } else {
                setEnabled(true);
            }
        }

        @Override
        public Component createMenuComponent() {
            return new JMenuItem(this);
        }

        @Override
        public boolean supportLayers(List<Layer> layers) {
            return true;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ShowHideLayerAction;
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }
    }

    public final class LayerOpacityAction extends AbstractAction implements IEnabledStateUpdating, LayerAction {
        private Layer layer;
        private JPopupMenu popup;
        private JSlider slider = new JSlider(JSlider.VERTICAL);

        /**
         * Creates a {@see LayerOpacityAction} which allows to chenge the
         * opacity of one or more layers.
         *
         * @param layer  the layer. Must not be null.
         * @exception IllegalArgumentException thrown, if layer is null
         */
        public LayerOpacityAction(Layer layer) throws IllegalArgumentException {
            this();
            putValue(NAME, tr("Opacity"));
            CheckParameterUtil.ensureParameterNotNull(layer, "layer");
            this.layer = layer;
            updateEnabledState();
        }

        /**
         * Creates a {@see ShowHideLayerAction} which will toggle the visibility of
         * the currently selected layers
         *
         */
        public LayerOpacityAction() {
            putValue(SHORT_DESCRIPTION, tr("Adjust opacity of the layer."));
            putValue(SMALL_ICON, ImageProvider.get("dialogs/layerlist", "transparency"));
            updateEnabledState();

            popup = new JPopupMenu();
            slider.addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    setOpacity((double)slider.getValue()/100);
                }
            });
            popup.add(slider);
        }

        private void setOpacity(double value) {
            if (!isEnabled()) return;
            if (layer != null) {
                layer.setOpacity(value);
            } else {
                for(Layer layer: model.getSelectedLayers()) {
                    layer.setOpacity(value);
                }
            }
        }

        private double getOpacity() {
            if (layer != null)
                return layer.getOpacity();
            else {
                double opacity = 0;
                List<Layer> layers = model.getSelectedLayers();
                for(Layer layer: layers) {
                    opacity += layer.getOpacity();
                }
                return opacity / layers.size();
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            slider.setValue((int)Math.round(getOpacity()*100));
            popup.show(opacityButton, 0, opacityButton.getHeight());
        }

        @Override
        public void updateEnabledState() {
            if (layer == null) {
                setEnabled(! getModel().getSelectedLayers().isEmpty());
            } else {
                setEnabled(true);
            }
        }

        @Override
        public Component createMenuComponent() {
            return new JMenuItem(this);
        }

        @Override
        public boolean supportLayers(List<Layer> layers) {
            return true;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof LayerOpacityAction;
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }
    }

    /**
     * The action to activate the currently selected layer
     */

    public final class ActivateLayerAction extends AbstractAction implements IEnabledStateUpdating, MapView.LayerChangeListener{
        private  Layer layer;

        public ActivateLayerAction(Layer layer) {
            this();
            CheckParameterUtil.ensureParameterNotNull(layer, "layer");
            this.layer = layer;
            putValue(NAME, tr("Activate"));
            updateEnabledState();
        }

        public ActivateLayerAction() {
            putValue(SMALL_ICON, ImageProvider.get("dialogs", "activate"));
            putValue(SHORT_DESCRIPTION, tr("Activate the selected layer"));
            putValue("help", HelpUtil.ht("/Dialog/LayerList#ActivateLayer"));
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Layer toActivate;
            if (layer != null) {
                toActivate = layer;
            } else {
                toActivate = model.getSelectedLayers().get(0);
            }
            // model is  going to be updated via LayerChangeListener
            // and PropertyChangeEvents
            Main.map.mapView.setActiveLayer(toActivate);
            toActivate.setVisible(true);
        }

        protected boolean isActiveLayer(Layer layer) {
            if (Main.map == null) return false;
            if (Main.map.mapView == null) return false;
            return Main.map.mapView.getActiveLayer() == layer;
        }

        @Override
        public void updateEnabledState() {
            if (layer == null) {
                if (getModel().getSelectedLayers().size() != 1) {
                    setEnabled(false);
                    return;
                }
                Layer selectedLayer = getModel().getSelectedLayers().get(0);
                setEnabled(!isActiveLayer(selectedLayer));
            } else {
                setEnabled(!isActiveLayer(layer));
            }
        }

        @Override
        public void activeLayerChange(Layer oldLayer, Layer newLayer) {
            updateEnabledState();
        }
        @Override
        public void layerAdded(Layer newLayer) {
            updateEnabledState();
        }
        @Override
        public void layerRemoved(Layer oldLayer) {
            updateEnabledState();
        }
    }

    /**
     * The action to merge the currently selected layer into another layer.
     */
    public final class MergeAction extends AbstractAction implements IEnabledStateUpdating {
        private  Layer layer;

        public MergeAction(Layer layer) throws IllegalArgumentException {
            this();
            CheckParameterUtil.ensureParameterNotNull(layer, "layer");
            this.layer = layer;
            putValue(NAME, tr("Merge"));
            updateEnabledState();
        }

        public MergeAction() {
            putValue(SMALL_ICON, ImageProvider.get("dialogs", "mergedown"));
            putValue(SHORT_DESCRIPTION, tr("Merge this layer into another layer"));
            putValue("help", HelpUtil.ht("/Dialog/LayerList#MergeLayer"));
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (layer != null) {
                new MergeLayerAction().merge(layer);
            } else {
                Layer selectedLayer = getModel().getSelectedLayers().get(0);
                new MergeLayerAction().merge(selectedLayer);
            }
        }

        protected boolean isActiveLayer(Layer layer) {
            if (Main.map == null) return false;
            if (Main.map.mapView == null) return false;
            return Main.map.mapView.getActiveLayer() == layer;
        }

        @Override
        public void updateEnabledState() {
            if (layer == null) {
                if (getModel().getSelectedLayers().size() != 1) {
                    setEnabled(false);
                    return;
                }
                Layer selectedLayer = getModel().getSelectedLayers().get(0);
                List<Layer> targets = getModel().getPossibleMergeTargets(selectedLayer);
                setEnabled(!targets.isEmpty());
            } else {
                List<Layer> targets = getModel().getPossibleMergeTargets(layer);
                setEnabled(!targets.isEmpty());
            }
        }
    }

    /**
     * The action to merge the currently selected layer into another layer.
     */
    public final class DuplicateAction extends AbstractAction implements IEnabledStateUpdating {
        private  Layer layer;

        public DuplicateAction(Layer layer) throws IllegalArgumentException {
            this();
            CheckParameterUtil.ensureParameterNotNull(layer, "layer");
            this.layer = layer;
            putValue(NAME, tr("Duplicate"));
            updateEnabledState();
        }

        public DuplicateAction() {
            putValue(SMALL_ICON, ImageProvider.get("dialogs", "duplicatelayer"));
            putValue(SHORT_DESCRIPTION, tr("Duplicate this layer"));
            putValue("help", HelpUtil.ht("/Dialog/LayerList#DuplicateLayer"));
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (layer != null) {
                new DuplicateLayerAction().duplicate(layer);
            } else {
                Layer selectedLayer = getModel().getSelectedLayers().get(0);
                new DuplicateLayerAction().duplicate(selectedLayer);
            }
        }

        protected boolean isActiveLayer(Layer layer) {
            if (Main.map == null) return false;
            if (Main.map.mapView == null) return false;
            return Main.map.mapView.getActiveLayer() == layer;
        }

        @Override
        public void updateEnabledState() {
            if (layer == null) {
                if (getModel().getSelectedLayers().size() == 1) {
                    setEnabled(DuplicateLayerAction.canDuplicate(getModel().getSelectedLayers().get(0)));
                } else {
                    setEnabled(false);
                }
            } else {
                setEnabled(DuplicateLayerAction.canDuplicate(layer));
            }
        }
    }

    private static class ActiveLayerCheckBox extends JCheckBox {
        public ActiveLayerCheckBox() {
            setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            ImageIcon blank = ImageProvider.get("dialogs/layerlist", "blank");
            ImageIcon active = ImageProvider.get("dialogs/layerlist", "active");
            setIcon(blank);
            setSelectedIcon(active);
            setRolloverIcon(blank);
            setRolloverSelectedIcon(active);
            setPressedIcon(ImageProvider.get("dialogs/layerlist", "active-pressed"));
        }
    }

    private static class LayerVisibleCheckBox extends JCheckBox {
        private final ImageIcon icon_eye;
        private final ImageIcon icon_eye_translucent;
        private boolean isTranslucent;
        public LayerVisibleCheckBox() {
            setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
            icon_eye = ImageProvider.get("dialogs/layerlist", "eye");
            icon_eye_translucent = ImageProvider.get("dialogs/layerlist", "eye-translucent");
            setIcon(ImageProvider.get("dialogs/layerlist", "eye-off"));
            setPressedIcon(ImageProvider.get("dialogs/layerlist", "eye-pressed"));
            setSelectedIcon(icon_eye);
            isTranslucent = false;
        }

        public void setTranslucent(boolean isTranslucent) {
            if (this.isTranslucent == isTranslucent) return;
            if (isTranslucent) {
                setSelectedIcon(icon_eye_translucent);
            } else {
                setSelectedIcon(icon_eye);
            }
            this.isTranslucent = isTranslucent;
        }

        public void updateStatus(Layer layer) {
            boolean visible = layer.isVisible();
            setSelected(visible);
            setTranslucent(layer.getOpacity()<1.0);
            setToolTipText(visible ? tr("layer is currently visible (click to hide layer)") : tr("layer is currently hidden (click to show layer)"));
        }
    }

    private static class ActiveLayerCellRenderer implements TableCellRenderer {
        JCheckBox cb;
        public ActiveLayerCellRenderer() {
            cb = new ActiveLayerCheckBox();
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            boolean active =  value != null && (Boolean) value;
            cb.setSelected(active);
            cb.setToolTipText(active ? tr("this layer is the active layer") : tr("this layer is not currently active (click to activate)"));
            return cb;
        }
    }

    private static class LayerVisibleCellRenderer implements TableCellRenderer {
        LayerVisibleCheckBox cb;
        public LayerVisibleCellRenderer() {
            this.cb = new LayerVisibleCheckBox();
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (value != null) {
                cb.updateStatus((Layer)value);
            }
            return cb;
        }
    }

    private static class LayerVisibleCellEditor extends DefaultCellEditor {
        LayerVisibleCheckBox cb;
        public LayerVisibleCellEditor(LayerVisibleCheckBox cb) {
            super(cb);
            this.cb = cb;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            cb.updateStatus((Layer)value);
            return cb;
        }
    }

    private static class LayerNameCellRenderer extends DefaultTableCellRenderer {

        protected boolean isActiveLayer(Layer layer) {
            if (Main.map == null) return false;
            if (Main.map.mapView == null) return false;
            return Main.map.mapView.getActiveLayer() == layer;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (value == null)
                return this;
            Layer layer = (Layer)value;
            JLabel label = (JLabel)super.getTableCellRendererComponent(table,
                    layer.getName(), isSelected, hasFocus, row, column);
            if (isActiveLayer(layer)) {
                label.setFont(label.getFont().deriveFont(Font.BOLD));
            }
            label.setIcon(layer.getIcon());
            label.setToolTipText(layer.getToolTipText());
            return label;
        }
    }

    private static class LayerNameCellEditor extends DefaultCellEditor {
        public LayerNameCellEditor(JTextField tf) {
            super(tf);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            JTextField tf = (JTextField) super.getTableCellEditorComponent(table, value, isSelected, row, column);
            tf.setText(value == null ? "" : ((Layer) value).getName());
            return tf;
        }
    }

    class PopupMenuHandler extends PopupMenuLauncher {
        @Override
        public void launch(MouseEvent evt) {
            Point p = evt.getPoint();
            int index = layerList.rowAtPoint(p);
            if (index < 0) return;
            if (!layerList.getCellRect(index, 2, false).contains(evt.getPoint()))
                return;
            if (!layerList.isRowSelected(index)) {
                layerList.setRowSelectionInterval(index, index);
            }
            Layer layer = model.getLayer(index);
            LayerListPopup menu = new LayerListPopup(getModel().getSelectedLayers(), layer);
            menu.show(layerList, p.x, p.y-3);
        }
    }

    /**
     * The action to move up the currently selected entries in the list.
     */
    class MoveUpAction extends AbstractAction implements  IEnabledStateUpdating{
        public MoveUpAction() {
            putValue(SMALL_ICON, ImageProvider.get("dialogs", "up"));
            putValue(SHORT_DESCRIPTION, tr("Move the selected layer one row up."));
            updateEnabledState();
        }

        @Override
        public void updateEnabledState() {
            setEnabled(model.canMoveUp());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            model.moveUp();
        }
    }

    /**
     * The action to move down the currently selected entries in the list.
     */
    class MoveDownAction extends AbstractAction implements IEnabledStateUpdating {
        public MoveDownAction() {
            putValue(SMALL_ICON, ImageProvider.get("dialogs", "down"));
            putValue(SHORT_DESCRIPTION, tr("Move the selected layer one row down."));
            updateEnabledState();
        }

        @Override
        public void updateEnabledState() {
            setEnabled(model.canMoveDown());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            model.moveDown();
        }
    }

    /**
     * Observer interface to be implemented by views using {@see LayerListModel}
     *
     */
    public interface LayerListModelListener {
        public void makeVisible(int index, Layer layer);
        public void refresh();
    }

    /**
     * The layer list model. The model manages a list of layers and provides methods for
     * moving layers up and down, for toggling their visibility, and for activating a layer.
     *
     * The model is a {@see TableModel} and it provides a {@see ListSelectionModel}. It expects
     * to be configured with a {@see DefaultListSelectionModel}. The selection model is used
     * to update the selection state of views depending on messages sent to the model.
     *
     * The model manages a list of {@see LayerListModelListener} which are mainly notified if
     * the model requires views to make a specific list entry visible.
     *
     * It also listens to {@see PropertyChangeEvent}s of every {@see Layer} it manages, in particular to
     * the properties {@see Layer#VISIBLE_PROP} and {@see Layer#NAME_PROP}.
     */
    public class LayerListModel extends AbstractTableModel implements MapView.LayerChangeListener, PropertyChangeListener {
        /** manages list selection state*/
        private DefaultListSelectionModel selectionModel;
        private CopyOnWriteArrayList<LayerListModelListener> listeners;

        /**
         * constructor
         *
         * @param selectionModel the list selection model
         */
        private LayerListModel(DefaultListSelectionModel selectionModel) {
            this.selectionModel = selectionModel;
            listeners = new CopyOnWriteArrayList<LayerListModelListener>();
        }

        /**
         * Adds a listener to this model
         *
         * @param listener the listener
         */
        public void addLayerListModelListener(LayerListModelListener listener) {
            if (listener != null) {
                listeners.addIfAbsent(listener);
            }
        }

        /**
         * removes a listener from  this model
         * @param listener the listener
         *
         */
        public void removeLayerListModelListener(LayerListModelListener listener) {
            listeners.remove(listener);
        }

        /**
         * Fires a make visible event to listeners
         *
         * @param index the index of the row to make visible
         * @param layer the layer at this index
         * @see LayerListModelListener#makeVisible(int, Layer)
         */
        protected void fireMakeVisible(int index, Layer layer) {
            for (LayerListModelListener listener : listeners) {
                listener.makeVisible(index, layer);
            }
        }

        /**
         * Fires a refresh event to listeners of this model
         *
         * @see LayerListModelListener#refresh()
         */
        protected void fireRefresh() {
            for (LayerListModelListener listener : listeners) {
                listener.refresh();
            }
        }

        /**
         * Populates the model with the current layers managed by
         * {@see MapView}.
         *
         */
        public void populate() {
            for (Layer layer: getLayers()) {
                // make sure the model is registered exactly once
                //
                layer.removePropertyChangeListener(this);
                layer.addPropertyChangeListener(this);
            }
            fireTableDataChanged();
        }

        /**
         * Marks <code>layer</code> as selected layer. Ignored, if
         * layer is null.
         *
         * @param layer the layer.
         */
        public void setSelectedLayer(Layer layer) {
            if (layer == null)
                return;
            int idx = getLayers().indexOf(layer);
            if (idx >= 0) {
                selectionModel.setSelectionInterval(idx, idx);
            }
            ensureSelectedIsVisible();
        }

        /**
         * Replies the list of currently selected layers. Never null, but may
         * be empty.
         *
         * @return the list of currently selected layers. Never null, but may
         * be empty.
         */
        public List<Layer> getSelectedLayers() {
            ArrayList<Layer> selected = new ArrayList<Layer>();
            for (int i=0; i<getLayers().size(); i++) {
                if (selectionModel.isSelectedIndex(i)) {
                    selected.add(getLayers().get(i));
                }
            }
            return selected;
        }

        /**
         * Replies a the list of indices of the selected rows. Never null,
         * but may be empty.
         *
         * @return  the list of indices of the selected rows. Never null,
         * but may be empty.
         */
        public List<Integer> getSelectedRows() {
            ArrayList<Integer> selected = new ArrayList<Integer>();
            for (int i=0; i<getLayers().size();i++) {
                if (selectionModel.isSelectedIndex(i)) {
                    selected.add(i);
                }
            }
            return selected;
        }

        /**
         * Invoked if a layer managed by {@see MapView} is removed
         *
         * @param layer the layer which is removed
         */
        protected void onRemoveLayer(Layer layer) {
            if (layer == null)
                return;
            layer.removePropertyChangeListener(this);
            int size = getRowCount();
            List<Integer> rows = getSelectedRows();
            if (rows.isEmpty() && size > 0) {
                selectionModel.setSelectionInterval(size-1, size-1);
            }
            fireTableDataChanged();
            fireRefresh();
            ensureActiveSelected();
        }

        /**
         * Invoked when a layer managed by {@see MapView} is added
         *
         * @param layer the layer
         */
        protected void onAddLayer(Layer layer) {
            if (layer == null) return;
            layer.addPropertyChangeListener(this);
            fireTableDataChanged();
            int idx = getLayers().indexOf(layer);
            layerList.setRowHeight(idx, Math.max(16, layer.getIcon().getIconHeight()));
            selectionModel.setSelectionInterval(idx, idx);
            ensureSelectedIsVisible();
        }

        /**
         * Replies the first layer. Null if no layers are present
         *
         * @return the first layer. Null if no layers are present
         */
        public Layer getFirstLayer() {
            if (getRowCount() == 0) return null;
            return getLayers().get(0);
        }

        /**
         * Replies the layer at position <code>index</code>
         *
         * @param index the index
         * @return the layer at position <code>index</code>. Null,
         * if index is out of range.
         */
        public Layer getLayer(int index) {
            if (index < 0 || index >= getRowCount())
                return null;
            return getLayers().get(index);
        }

        /**
         * Replies true if the currently selected layers can move up
         * by one position
         *
         * @return true if the currently selected layers can move up
         * by one position
         */
        public boolean canMoveUp() {
            List<Integer> sel = getSelectedRows();
            return !sel.isEmpty() && sel.get(0) > 0;
        }

        /**
         * Move up the currently selected layers by one position
         *
         */
        public void moveUp() {
            if (!canMoveUp()) return;
            List<Integer> sel = getSelectedRows();
            for (int row : sel) {
                Layer l1 = getLayers().get(row);
                Layer l2 = getLayers().get(row-1);
                Main.map.mapView.moveLayer(l2,row);
                Main.map.mapView.moveLayer(l1, row-1);
            }
            fireTableDataChanged();
            selectionModel.clearSelection();
            for (int row : sel) {
                selectionModel.addSelectionInterval(row-1, row-1);
            }
            ensureSelectedIsVisible();
        }

        /**
         * Replies true if the currently selected layers can move down
         * by one position
         *
         * @return true if the currently selected layers can move down
         * by one position
         */
        public boolean canMoveDown() {
            List<Integer> sel = getSelectedRows();
            return !sel.isEmpty() && sel.get(sel.size()-1) < getLayers().size()-1;
        }

        /**
         * Move down the currently selected layers by one position
         *
         */
        public void moveDown() {
            if (!canMoveDown()) return;
            List<Integer> sel = getSelectedRows();
            Collections.reverse(sel);
            for (int row : sel) {
                Layer l1 = getLayers().get(row);
                Layer l2 = getLayers().get(row+1);
                Main.map.mapView.moveLayer(l1, row+1);
                Main.map.mapView.moveLayer(l2, row);
            }
            fireTableDataChanged();
            selectionModel.clearSelection();
            for (int row : sel) {
                selectionModel.addSelectionInterval(row+1, row+1);
            }
            ensureSelectedIsVisible();
        }

        /**
         * Make sure the first of the selected layers is visible in the
         * views of this model.
         *
         */
        protected void ensureSelectedIsVisible() {
            int index = selectionModel.getMinSelectionIndex();
            if (index < 0) return;
            if (index >= getLayers().size()) return;
            Layer layer = getLayers().get(index);
            fireMakeVisible(index, layer);
        }

        /**
         * Replies a list of layers which are possible merge targets
         * for <code>source</code>
         *
         * @param source the source layer
         * @return a list of layers which are possible merge targets
         * for <code>source</code>. Never null, but can be empty.
         */
        public List<Layer> getPossibleMergeTargets(Layer source) {
            ArrayList<Layer> targets = new ArrayList<Layer>();
            if (source == null)
                return targets;
            for (Layer target : getLayers()) {
                if (source == target) {
                    continue;
                }
                if (target.isMergable(source)) {
                    targets.add(target);
                }
            }
            return targets;
        }

        /**
         * Replies the list of layers currently managed by {@see MapView}.
         * Never null, but can be empty.
         *
         * @return the list of layers currently managed by {@see MapView}.
         * Never null, but can be empty.
         */
        protected List<Layer> getLayers() {
            if (Main.map == null || Main.map.mapView == null)
                return Collections.<Layer>emptyList();
            return Main.map.mapView.getAllLayersAsList();
        }

        /**
         * Ensures that at least one layer is selected in the layer dialog
         *
         */
        protected void ensureActiveSelected() {
            if (getLayers().isEmpty())
                return;
            if (getActiveLayer() != null) {
                // there's an active layer - select it and make it
                // visible
                int idx = getLayers().indexOf(getActiveLayer());
                selectionModel.setSelectionInterval(idx, idx);
                ensureSelectedIsVisible();
            } else {
                // no active layer - select the first one and make
                // it visible
                selectionModel.setSelectionInterval(0, 0);
                ensureSelectedIsVisible();
            }
        }

        /**
         * Replies the active layer. null, if no active layer is available
         *
         * @return the active layer. null, if no active layer is available
         */
        protected Layer getActiveLayer() {
            if (Main.map == null || Main.map.mapView == null) return null;
            return Main.map.mapView.getActiveLayer();
        }

        /* ------------------------------------------------------------------------------ */
        /* Interface TableModel                                                           */
        /* ------------------------------------------------------------------------------ */

        @Override
        public int getRowCount() {
            List<Layer> layers = getLayers();
            if (layers == null) return 0;
            return layers.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public Object getValueAt(int row, int col) {
            switch (col) {
            case 0: return getLayers().get(row) == getActiveLayer();
            case 1: return getLayers().get(row);
            case 2: return getLayers().get(row);
            default: throw new RuntimeException();
            }
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            if (col == 0 && getActiveLayer() == getLayers().get(row))
                return false;
            return true;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            Layer l = getLayers().get(row);
            switch (col) {
            case 0:
                Main.map.mapView.setActiveLayer(l);
                l.setVisible(true);
                break;
            case 1:
                l.setVisible((Boolean) value);
                break;
            case 2:
                l.setName((String) value);
                break;
            default: throw new RuntimeException();
            }
            fireTableCellUpdated(row, col);
        }

        /* ------------------------------------------------------------------------------ */
        /* Interface LayerChangeListener                                                  */
        /* ------------------------------------------------------------------------------ */
        @Override
        public void activeLayerChange(Layer oldLayer, Layer newLayer) {
            if (oldLayer != null) {
                int idx = getLayers().indexOf(oldLayer);
                if (idx >= 0) {
                    fireTableRowsUpdated(idx,idx);
                }
            }

            if (newLayer != null) {
                int idx = getLayers().indexOf(newLayer);
                if (idx >= 0) {
                    fireTableRowsUpdated(idx,idx);
                }
            }
            ensureActiveSelected();
        }

        @Override
        public void layerAdded(Layer newLayer) {
            onAddLayer(newLayer);
        }

        @Override
        public void layerRemoved(final Layer oldLayer) {
            onRemoveLayer(oldLayer);
        }

        /* ------------------------------------------------------------------------------ */
        /* Interface PropertyChangeListener                                               */
        /* ------------------------------------------------------------------------------ */
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getSource() instanceof Layer) {
                Layer layer = (Layer)evt.getSource();
                final int idx = getLayers().indexOf(layer);
                if (idx < 0) return;
                fireRefresh();
            }
        }
    }

    static class LayerList extends JTable {
        public LayerList(TableModel dataModel) {
            super(dataModel);
        }

        public void scrollToVisible(int row, int col) {
            if (!(getParent() instanceof JViewport))
                return;
            JViewport viewport = (JViewport) getParent();
            Rectangle rect = getCellRect(row, col, true);
            Point pt = viewport.getViewPosition();
            rect.setLocation(rect.x - pt.x, rect.y - pt.y);
            viewport.scrollRectToVisible(rect);
        }
    }

    /**
     * Creates a {@see ShowHideLayerAction} for <code>layer</code> in the
     * context of this {@see LayerListDialog}.
     *
     * @param layer the layer
     * @return the action
     */
    public ShowHideLayerAction createShowHideLayerAction() {
        ShowHideLayerAction act = new ShowHideLayerAction();
        act.putValue(Action.NAME, tr("Show/Hide"));
        return act;
    }

    /**
     * Creates a {@see DeleteLayerAction} for <code>layer</code> in the
     * context of this {@see LayerListDialog}.
     *
     * @param layer the layer
     * @return the action
     */
    public DeleteLayerAction createDeleteLayerAction() {
        // the delete layer action doesn't depend on the current layer
        return new DeleteLayerAction();
    }

    /**
     * Creates a {@see ActivateLayerAction} for <code>layer</code> in the
     * context of this {@see LayerListDialog}.
     *
     * @param layer the layer
     * @return the action
     */
    public ActivateLayerAction createActivateLayerAction(Layer layer) {
        return new ActivateLayerAction(layer);
    }

    /**
     * Creates a {@see MergeLayerAction} for <code>layer</code> in the
     * context of this {@see LayerListDialog}.
     *
     * @param layer the layer
     * @return the action
     */
    public MergeAction createMergeLayerAction(Layer layer) {
        return new MergeAction(layer);
    }
}
