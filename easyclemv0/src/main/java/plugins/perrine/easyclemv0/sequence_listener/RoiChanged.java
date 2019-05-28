package plugins.perrine.easyclemv0.sequence_listener;

import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceListener;
import plugins.perrine.easyclemv0.model.WorkspaceState;
import plugins.perrine.easyclemv0.model.WorkspaceTransformer;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;

public class RoiChanged implements SequenceListener {

    private Sequence sequence;
    private WorkspaceState workspaceState;
    private WorkspaceTransformer workspaceTransformer;
//    private CompletionService<Runnable> completionService = new ExecutorCompletionService<>(
//        Executors.newSingleThreadExecutor()
//    );

    public RoiChanged(WorkspaceState workspaceState, WorkspaceTransformer workspaceTransformer, Sequence sequence) {
        this.sequence = sequence;
        this.workspaceState = workspaceState;
        this.workspaceTransformer = workspaceTransformer;
    }

    @Override
    public void sequenceChanged(SequenceEvent event) {
        if (workspaceState.isStopFlag()) {
            return;
        }

        if (!workspaceState.isFlagReadyToMove()) {
            return;
        }

        if (event.getSourceType() != SequenceEvent.SequenceEventSourceType.SEQUENCE_ROI) {
            return;
        }


        if (event.getType() != SequenceEvent.SequenceEventType.CHANGED) {
            return;
        }

        if (!workspaceState.isDone()) {
//            SequenceListener[] eventSequenceListeners = removeListeners(event.getSequence());
//            SequenceListener[] sequenceListeners = removeListeners(sequence);
            workspaceTransformer.run();
//            addListeners(event.getSequence(), eventSequenceListeners);
//            addListeners(sequence, sequenceListeners);
            workspaceState.setDone(true);
        }
    }

//    private SequenceListener[] removeListeners(Sequence sequence) {
//        SequenceListener[] listeners = sequence.getListeners();
//        for(SequenceListener listener : listeners) {
//            sequence.removeListener(listener);
//        }
//        return listeners;
//    }
//
//    private void addListeners(Sequence sequence, SequenceListener[] listeners) {
//        for(SequenceListener listener : listeners) {
//            sequence.addListener(listener);
//        }
//    }

    @Override
    public void sequenceClosed(Sequence sequence) {

    }
}
