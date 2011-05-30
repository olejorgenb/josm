// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import org.openstreetmap.josm.plugins.PluginHandler;
import org.openstreetmap.josm.tools.Shortcut;

public class ReloadPluginsAction extends JosmAction {

    public ReloadPluginsAction() {
        super(tr("Reload plugins"), null, tr("Reload plugins"), Shortcut.registerShortcut("system:reload-plugins",
                tr("Reload plugins"), KeyEvent.VK_F5, Shortcut.GROUP_DIRECT, Shortcut.SHIFT_DEFAULT), true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        PluginHandler.reloadPlugins();
    }
}
