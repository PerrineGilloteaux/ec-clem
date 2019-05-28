package plugins.perrine.easyclemv0.model;

import icy.sequence.Sequence;
import plugins.perrine.easyclemv0.monitor.MonitoringConfiguration;

import java.io.File;

public class Workspace {

    private Sequence sourceSequence;
    private Sequence targetSequence;
    private Sequence sourceBackup;
    private File XMLFile;
    private boolean nonRigid;
    private WorkspaceState workspaceState;

    public Workspace() {
        nonRigid = false;
        workspaceState = new WorkspaceState(false, false, false, false);
        monitoringConfiguration = new MonitoringConfiguration(false, false);
    }

    public Workspace(Sequence sourceSequence, Sequence targetSequence, Sequence sourceBackup, File XMLFile, boolean nonRigid, WorkspaceState workspaceState, MonitoringConfiguration monitoringConfiguration) {
        this.sourceSequence = sourceSequence;
        this.targetSequence = targetSequence;
        this.sourceBackup = sourceBackup;
        this.XMLFile = XMLFile;
        this.nonRigid = nonRigid;
        this.workspaceState = workspaceState;
        this.monitoringConfiguration = monitoringConfiguration;
    }

    private MonitoringConfiguration monitoringConfiguration;

    public Sequence getSourceSequence() {
        return sourceSequence;
    }

    public void setSourceSequence(Sequence sourceSequence) {
        this.sourceSequence = sourceSequence;
    }

    public Sequence getSourceBackup() {
        return sourceBackup;
    }

    public void setSourceBackup(Sequence sourceBackup) {
        this.sourceBackup = sourceBackup;
    }

    public Sequence getTargetSequence() {
        return targetSequence;
    }

    public void setTargetSequence(Sequence targetSequence) {
        this.targetSequence = targetSequence;
    }

    public File getXMLFile() {
        return XMLFile;
    }

    public void setXMLFile(File XMLFile) {
        this.XMLFile = XMLFile;
    }

    public boolean isNonRigid() {
        return nonRigid;
    }

    public void setNonRigid(boolean nonRigid) {
        this.nonRigid = nonRigid;
    }

    public WorkspaceState getWorkspaceState() {
        return workspaceState;
    }

    public void setWorkspaceState(WorkspaceState workspaceState) {
        this.workspaceState = workspaceState;
    }

    public MonitoringConfiguration getMonitoringConfiguration() {
        return monitoringConfiguration;
    }

    public void setMonitoringConfiguration(MonitoringConfiguration monitoringConfiguration) {
        this.monitoringConfiguration = monitoringConfiguration;
    }
}
