package org.tvheadend.tvhclient.data.service.worker;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import org.tvheadend.tvhclient.data.service.EpgSyncIntentService;

import androidx.work.Worker;
import androidx.work.WorkerParameters;
import timber.log.Timber;

public class EpgDataRemovalWorker extends Worker {

    public EpgDataRemovalWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Timber.d("Removing outdated epg data from the database");
        EpgSyncIntentService.enqueueWork(getApplicationContext(), new Intent().setAction("deleteEvents"));
        return Result.success();
    }
}
