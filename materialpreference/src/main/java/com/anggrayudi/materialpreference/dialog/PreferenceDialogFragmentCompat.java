/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.anggrayudi.materialpreference.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.anggrayudi.materialpreference.PreferenceFragmentMaterial;

import static android.support.annotation.RestrictTo.Scope.LIBRARY_GROUP;

/**
 * Abstract base class which presents a dialog associated with a
 * {@link DialogPreference}. Since the preference object may
 * not be available during fragment re-creation, the necessary information for displaying the dialog
 * is read once during the initial call to {@link #onCreate(Bundle)} and saved/restored in the saved
 * instance state. Custom subclasses should also follow this pattern.
 */
public abstract class PreferenceDialogFragmentCompat extends DialogFragment implements MaterialDialog.SingleButtonCallback {

    protected static final String ARG_KEY = "key";

    private static final String SAVE_STATE_TITLE = "PreferenceDialogFragment.title";
    private static final String SAVE_STATE_POSITIVE_TEXT = "PreferenceDialogFragment.positiveText";
    private static final String SAVE_STATE_NEGATIVE_TEXT = "PreferenceDialogFragment.negativeText";
    private static final String SAVE_STATE_MESSAGE = "PreferenceDialogFragment.message";
    private static final String SAVE_STATE_LAYOUT = "PreferenceDialogFragment.layout";
    private static final String SAVE_STATE_ICON = "PreferenceDialogFragment.icon";

    private DialogPreference mPreference;

    private CharSequence mDialogTitle;
    private CharSequence mPositiveButtonText;
    private CharSequence mNegativeButtonText;
    private CharSequence mDialogMessage;
    private @LayoutRes int mDialogLayoutRes;

    private BitmapDrawable mDialogIcon;

    /** Which button was clicked. */
    private DialogAction mWhichButtonClicked;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Fragment rawFragment = getTargetFragment();
        if (!(rawFragment instanceof DialogPreference.TargetFragment)) {
            throw new IllegalStateException("Target fragment must implement TargetFragment" +
                    " interface");
        }

        final DialogPreference.TargetFragment fragment =
                (DialogPreference.TargetFragment) rawFragment;

        final String key = getArguments().getString(ARG_KEY);
        if (savedInstanceState == null) {
            mPreference = (DialogPreference) fragment.findPreference(key);
            mDialogTitle = mPreference.getDialogTitle();
            mPositiveButtonText = mPreference.getPositiveButtonText();
            mNegativeButtonText = mPreference.getNegativeButtonText();
            mDialogMessage = mPreference.getDialogMessage();
            mDialogLayoutRes = mPreference.getDialogLayoutResource();

            final Drawable icon = mPreference.getDialogIcon();
            if (icon == null || icon instanceof BitmapDrawable) {
                mDialogIcon = (BitmapDrawable) icon;
            } else {
                final Bitmap bitmap = Bitmap.createBitmap(icon.getIntrinsicWidth(),
                        icon.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                final Canvas canvas = new Canvas(bitmap);
                icon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                icon.draw(canvas);
                mDialogIcon = new BitmapDrawable(getResources(), bitmap);
            }
        } else {
            mDialogTitle = savedInstanceState.getCharSequence(SAVE_STATE_TITLE);
            mPositiveButtonText = savedInstanceState.getCharSequence(SAVE_STATE_POSITIVE_TEXT);
            mNegativeButtonText = savedInstanceState.getCharSequence(SAVE_STATE_NEGATIVE_TEXT);
            mDialogMessage = savedInstanceState.getCharSequence(SAVE_STATE_MESSAGE);
            mDialogLayoutRes = savedInstanceState.getInt(SAVE_STATE_LAYOUT, 0);
            final Bitmap bitmap = savedInstanceState.getParcelable(SAVE_STATE_ICON);
            if (bitmap != null) {
                mDialogIcon = new BitmapDrawable(getResources(), bitmap);
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putCharSequence(SAVE_STATE_TITLE, mDialogTitle);
        outState.putCharSequence(SAVE_STATE_POSITIVE_TEXT, mPositiveButtonText);
        outState.putCharSequence(SAVE_STATE_NEGATIVE_TEXT, mNegativeButtonText);
        outState.putCharSequence(SAVE_STATE_MESSAGE, mDialogMessage);
        outState.putInt(SAVE_STATE_LAYOUT, mDialogLayoutRes);
        if (mDialogIcon != null) {
            outState.putParcelable(SAVE_STATE_ICON, mDialogIcon.getBitmap());
        }
    }

    @Override
    public @NonNull
    Dialog onCreateDialog(Bundle savedInstanceState) {
        final Context context = getActivity();
        mWhichButtonClicked = DialogAction.NEGATIVE;

        MaterialDialog.Builder builder = new MaterialDialog.Builder(context)
                .title(mDialogTitle)
                .icon(mDialogIcon)
                .positiveText(mPositiveButtonText)
                .negativeText(mNegativeButtonText)
                .onPositive(this)
                .onNegative(this);

        View contentView = onCreateDialogView(context);
        if (contentView != null) {
            onBindDialogView(contentView);
            builder.customView(contentView, true);
        } else {
            builder.content(mDialogMessage);
        }

        onPrepareDialogBuilder(builder);

        // Create the dialog
        final Dialog dialog = builder.build();
        if (needInputMethod()) {
            requestInputMethod(dialog);
        }

        return dialog;
    }

    /**
     * Get the preference that requested this dialog. Available after {@link #onCreate(Bundle)} has
     * been called on the {@link PreferenceFragmentMaterial} which launched this dialog.
     *
     * @return The {@link DialogPreference} associated with this
     * dialog.
     */
    public DialogPreference getPreference() {
        if (mPreference == null) {
            final String key = getArguments().getString(ARG_KEY);
            final DialogPreference.TargetFragment fragment =
                    (DialogPreference.TargetFragment) getTargetFragment();
            mPreference = (DialogPreference) fragment.findPreference(key);
        }
        return mPreference;
    }

    /**
     * Prepares the dialog builder to be shown when the preference is clicked.
     * Use this to set custom properties on the dialog.
     * <p>
     * Do not {@link AlertDialog.Builder#create()} or
     * {@link AlertDialog.Builder#show()}.
     */
    protected void onPrepareDialogBuilder(MaterialDialog.Builder builder) {
    }

    /**
     * Returns whether the preference needs to display a soft input method when the dialog
     * is displayed. Default is false. Subclasses should override this method if they need
     * the soft input method brought up automatically.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    protected boolean needInputMethod() {
        return false;
    }

    /**
     * Sets the required flags on the dialog window to enable input method window to show up.
     */
    private void requestInputMethod(Dialog dialog) {
        Window window = dialog.getWindow();
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    /**
     * Creates the content view for the dialog (if a custom content view is
     * required). By default, it inflates the dialog layout resource if it is
     * set.
     *
     * @return The content View for the dialog.
     * @see DialogPreference#setLayoutResource(int)
     */
    protected View onCreateDialogView(Context context) {
        final int resId = mDialogLayoutRes;
        if (resId == 0) {
            return null;
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        return inflater.inflate(resId, null);
    }

    /**
     * Binds views in the content View of the dialog to data.
     * <p>
     * Make sure to call through to the superclass implementation.
     *
     * @param view The content View of the dialog, if it is custom.
     */
    protected void onBindDialogView(View view) {
        View dialogMessageView = view.findViewById(android.R.id.message);

        if (dialogMessageView != null) {
            final CharSequence message = mDialogMessage;
            int newVisibility = View.GONE;

            if (!TextUtils.isEmpty(message)) {
                if (dialogMessageView instanceof TextView) {
                    ((TextView) dialogMessageView).setText(message);
                }

                newVisibility = View.VISIBLE;
            }

            if (dialogMessageView.getVisibility() != newVisibility) {
                dialogMessageView.setVisibility(newVisibility);
            }
        }
    }

    @Override
    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
        mWhichButtonClicked = which;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        onDialogClosed(mWhichButtonClicked == DialogAction.POSITIVE);
    }

    public abstract void onDialogClosed(boolean positiveResult);
}
