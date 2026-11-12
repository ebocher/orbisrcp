/*
 * H2GIS ecplise plugin to register a H2GIS spatial database to 
 * DBeaver, the  Universal Database Manager
 *
 * For more information, please consult: <http://www.h2gis.org/>
 * or contact directly: info_at_h2gis.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.orbisgis.h2gis.engine;

import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.BooleanSupplier;

/**
 * Cancels the running statement when the DBeaver job is canceled: a thread watches the monitor while a statement
 * runs, then cancels it, with an action of the engine (cancel of the H2 session), again until the statement ends.
 */
public final class CancelWatcher implements AutoCloseable {

    private static final long PERIOD = 200;

    private final BooleanSupplier canceledByUser;
    private final Runnable onCancel;
    private final Thread thread;
    private volatile Statement statement;
    private volatile boolean canceled;
    private volatile boolean closed;

    /**
     * @param monitor the monitor of the job, null for no cancel
     */
    public CancelWatcher(DBRProgressMonitor monitor) {
        this(monitor, () -> {
        });
    }

    /**
     * @param onCancel run with the cancel of the statement (it must not throw)
     */
    public CancelWatcher(DBRProgressMonitor monitor, Runnable onCancel) {
        this(monitor == null ? () -> false : monitor::isCanceled, onCancel);
    }

    CancelWatcher(BooleanSupplier canceledByUser, Runnable onCancel) {
        this.canceledByUser = canceledByUser;
        this.onCancel = onCancel;
        thread = new Thread(this::watch, "H2GIS files cancel");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * The statement running now: canceled at once if the job is already canceled.
     */
    public void watch(Statement running) {
        statement = running;
    }

    /**
     * @return true if the job was canceled while a statement ran
     */
    public boolean isCanceled() {
        return canceled;
    }

    private void watch() {
        try {
            while (!closed) {
                if (canceled || canceledByUser.getAsBoolean()) {
                    // Again at each period: a cancel between two statements has no effect
                    canceled = true;
                    Statement running = statement;
                    if (running != null) {
                        cancel(running);
                    }
                    onCancel.run();
                }
                Thread.sleep(PERIOD);
            }
        } catch (InterruptedException e) {
            // closed
        }
    }

    private static void cancel(Statement running) {
        try {
            running.cancel();
        } catch (SQLException | RuntimeException e) {
            // closed meanwhile, or not supported: the statement ends normally
        }
    }

    @Override
    public void close() {
        closed = true;
        thread.interrupt();
    }
}
