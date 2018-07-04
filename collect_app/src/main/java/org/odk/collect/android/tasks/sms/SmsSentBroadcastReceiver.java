package org.odk.collect.android.tasks.sms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;

import org.odk.collect.android.application.Collect;
import org.odk.collect.android.tasks.sms.models.SmsSendResultStatus;
import org.odk.collect.android.tasks.sms.models.SentMessageResult;

import javax.inject.Inject;

import timber.log.Timber;

import static org.odk.collect.android.tasks.sms.SmsNotificationReceiver.SMS_MESSAGE_RESULT;
import static org.odk.collect.android.tasks.sms.SmsNotificationReceiver.SMS_NOTIFICATION_ACTION;
import static org.odk.collect.android.tasks.sms.SmsSender.SMS_INSTANCE_ID;
import static org.odk.collect.android.tasks.sms.SmsSender.SMS_MESSAGE_ID;

/***
 * Receives events from the SMSManager when a SMS has been sent.
 * This intent is triggered by the SMSSenderJob that's sending different
 * parts of a form each time it's triggered.
 */
public class SmsSentBroadcastReceiver extends BroadcastReceiver {

    @Inject
    SmsService smsService;

    @Override
    public void onReceive(Context context, Intent intent) {

        Collect.getInstance().getComponent().inject(this);

        SentMessageResult result = new SentMessageResult();

        if (intent.getExtras() == null) {
            Timber.e("getExtras returned a null value.");
            return;
        }

        result.setMessageId(intent.getExtras().getInt(SMS_MESSAGE_ID));
        result.setInstanceId(intent.getExtras().getString(SMS_INSTANCE_ID));

        switch (getResultCode()) {
            case Activity.RESULT_OK:
                result.setSmsSendResultStatus(SmsSendResultStatus.Sent);
                break;
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                result.setSmsSendResultStatus(SmsSendResultStatus.FatalError);
                break;
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                result.setSmsSendResultStatus(SmsSendResultStatus.NoReception);
                break;
            case SmsManager.RESULT_ERROR_NULL_PDU:
                result.setSmsSendResultStatus(SmsSendResultStatus.FatalError);
                break;
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                result.setSmsSendResultStatus(SmsSendResultStatus.AirplaneMode);

                break;
        }

        smsService.processMessageSentResult(result);

        Intent notificationIntent = new Intent(SMS_NOTIFICATION_ACTION);
        notificationIntent.putExtra(SMS_MESSAGE_RESULT, result);
        context.sendOrderedBroadcast(notificationIntent, null);
    }
}
