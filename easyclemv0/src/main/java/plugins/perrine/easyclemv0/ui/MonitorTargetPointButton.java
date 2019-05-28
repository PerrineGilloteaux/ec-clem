package plugins.perrine.easyclemv0.ui;

import icy.plugin.PluginDescriptor;
import icy.plugin.PluginLauncher;
import icy.plugin.PluginLoader;
import plugins.perrine.easyclemv0.model.Workspace;
import plugins.perrine.easyclemv0.monitor.MonitorTargetOverlay;
import plugins.perrine.easyclemv0.monitor.MonitorTargetPoint;

import javax.swing.*;

public class MonitorTargetPointButton extends JButton {

    private Workspace workspace;

    public MonitorTargetPointButton(Workspace workspace) {
        super("Monitor a target point ");
        setToolTipText(" This will display the evolution of the target registration error at one target position while points are added");
        this.workspace = workspace;
        addActionListener((arg0) -> action());
    }

    private void action() {
        PluginDescriptor plugin = PluginLoader.getPlugin(MonitorTargetPoint.class.getName());
        MonitorTargetOverlay monitorTargetOverlay = new MonitorTargetOverlay(
            workspace.getMonitoringConfiguration()
        );
        workspace.getTargetSequence().addOverlay(monitorTargetOverlay);
        PluginLauncher.start(plugin);
    }
}
