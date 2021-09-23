package org.odk.collect.android.configure.qr;

import android.content.Intent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import org.odk.collect.android.R;
import org.odk.collect.android.preferences.source.SettingsProvider;
import org.odk.collect.android.utilities.ActivityAvailability;
import org.odk.collect.android.utilities.FileProvider;
import org.odk.collect.android.utilities.MenuDelegate;
import org.odk.collect.androidshared.utils.ToastUtils;
import org.odk.collect.async.Scheduler;

import timber.log.Timber;

public class QRCodeMenuDelegate implements MenuDelegate {

    public static final int SELECT_PHOTO = 111;

    private final FragmentActivity activity;
    private final ActivityAvailability activityAvailability;
    private final FileProvider fileProvider;

    private String qrFilePath;

    QRCodeMenuDelegate(FragmentActivity activity, ActivityAvailability activityAvailability, QRCodeGenerator qrCodeGenerator,
                       AppConfigurationGenerator appConfigurationGenerator, FileProvider fileProvider,
                       SettingsProvider settingsProvider, Scheduler scheduler) {
        this.activity = activity;
        this.activityAvailability = activityAvailability;
        this.fileProvider = fileProvider;

        QRCodeViewModel qrCodeViewModel = new ViewModelProvider(
                activity,
                new QRCodeViewModel.Factory(qrCodeGenerator, appConfigurationGenerator, settingsProvider, scheduler)
        ).get(QRCodeViewModel.class);
        qrCodeViewModel.getFilePath().observe(activity, filePath -> {
            if (filePath != null) {
                this.qrFilePath = filePath;
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(MenuInflater menuInflater, Menu menu) {
        menuInflater.inflate(R.menu.qr_code_scan_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_scan_sd_card:
                Intent photoPickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
                photoPickerIntent.setType("image/*");
                if (activityAvailability.isActivityAvailable(photoPickerIntent)) {
                    activity.startActivityForResult(photoPickerIntent, SELECT_PHOTO);
                } else {
                    ToastUtils.showShortToast(activity, activity.getString(R.string.activity_not_found, activity.getString(R.string.choose_image)));
                    Timber.w(activity.getString(R.string.activity_not_found, activity.getString(R.string.choose_image)));
                }

                return true;

            case R.id.menu_item_share:
                if (qrFilePath != null) {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_SEND);
                    intent.setType("image/*");
                    intent.putExtra(Intent.EXTRA_STREAM, fileProvider.getURIForFile(qrFilePath));
                    activity.startActivity(intent);
                }

                return true;
        }

        return false;
    }


    @Override
    public void onPrepareOptionsMenu(Menu menu) {

    }
}
