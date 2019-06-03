package com.randomappsinc.objectrecognition;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.StringRes;

class UIUtils {

    static void showLongToast(@StringRes int stringId, Context context) {
        showToast(stringId, Toast.LENGTH_LONG, context);
    }

    private static void showToast(@StringRes int stringId, int duration, Context context) {
        Toast.makeText(context, stringId, duration).show();
    }
}
