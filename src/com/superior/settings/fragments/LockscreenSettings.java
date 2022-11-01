/*
 * Copyright (C) 2019-2021 The Evolution X Project
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
 * limitations under the License.
 */

package com.superior.settings.fragments;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.fingerprint.FingerprintManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.SwitchPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.superior.SuperiorUtils;
import com.android.internal.util.superior.fod.FodUtils;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.List;

import com.superior.settings.preferences.SystemSettingSeekBarPreference;
import com.superior.settings.utils.Utils;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class LockscreenSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private boolean mHasFod;

    private static final String AOD_SCHEDULE_KEY = "always_on_display_schedule";
    private static final String FINGERPRINT_ERROR_VIB = "fingerprint_error_vib";
    private static final String FINGERPRINT_VIB = "fingerprint_success_vib";
    private static final String FOD_ANIMATION_CATEGORY = "fod_animations";
    private static final String FOD_ICON_PICKER_CATEGORY = "fod_icon_picker";
    private static final String KEY_LOCKSCREEN_BLUR = "lockscreen_blur";

    private FingerprintManager mFingerprintManager;
    private PreferenceCategory mFODIconPickerCategory;
    private SwitchPreference mFingerprintErrorVib;
    private SwitchPreference mFingerprintVib;
    private SystemSettingSeekBarPreference mLockscreenBlur;

    static final int MODE_DISABLED = 0;
    static final int MODE_NIGHT = 1;
    static final int MODE_TIME = 2;
    static final int MODE_MIXED_SUNSET = 3;
    static final int MODE_MIXED_SUNRISE = 4;

    Preference mAODPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.superior_settings_lockscreen);
        Context mContext = getContext();
        PreferenceScreen prefScreen = getPreferenceScreen();
        final PackageManager mPm = getActivity().getPackageManager();
        WallpaperManager manager = WallpaperManager.getInstance(mContext);

        mFingerprintManager = (FingerprintManager) getActivity().getSystemService(Context.FINGERPRINT_SERVICE);
        mFingerprintVib = (SwitchPreference) findPreference(FINGERPRINT_VIB);
        if (mPm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) &&
                 mFingerprintManager != null) {
            if (!mFingerprintManager.isHardwareDetected()){
                prefScreen.removePreference(mFingerprintVib);
            } else {
                mFingerprintVib.setChecked((Settings.System.getInt(getContentResolver(),
                        Settings.System.FINGERPRINT_SUCCESS_VIB, 1) == 1));
                mFingerprintVib.setOnPreferenceChangeListener(this);
            }
        } else {
            prefScreen.removePreference(mFingerprintVib);
        }

        mFingerprintManager = (FingerprintManager) getActivity().getSystemService(Context.FINGERPRINT_SERVICE);
        mFingerprintErrorVib = (SwitchPreference) findPreference(FINGERPRINT_ERROR_VIB);
        if (mPm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) &&
                 mFingerprintManager != null) {
            if (!mFingerprintManager.isHardwareDetected()){
                prefScreen.removePreference(mFingerprintErrorVib);
            } else {
                mFingerprintErrorVib.setChecked((Settings.System.getInt(getContentResolver(),
                        Settings.System.FINGERPRINT_ERROR_VIB, 1) == 1));
                mFingerprintErrorVib.setOnPreferenceChangeListener(this);
            }
        } else {
            prefScreen.removePreference(mFingerprintErrorVib);
        }

        ParcelFileDescriptor pfd = manager.getWallpaperFile(WallpaperManager.FLAG_LOCK);
        mLockscreenBlur = (SystemSettingSeekBarPreference) findPreference(KEY_LOCKSCREEN_BLUR);
        if (!Utils.isBlurSupported() || pfd != null) {
            mLockscreenBlur.setVisible(false);
        }

        mFODIconPickerCategory = findPreference(FOD_ICON_PICKER_CATEGORY);
        if (mFODIconPickerCategory != null && !FodUtils.hasFodSupport(getContext())) {
            prefScreen.removePreference(mFODIconPickerCategory);
        }

        final PreferenceCategory fodCat = (PreferenceCategory) prefScreen
                .findPreference(FOD_ANIMATION_CATEGORY);
        final boolean isFodAnimationResources = SuperiorUtils.isPackageInstalled(getContext(),
                      getResources().getString(com.android.internal.R.string.config_fodAnimationPackage));
        if (!isFodAnimationResources) {
            prefScreen.removePreference(fodCat);
        }

        mAODPref = findPreference(AOD_SCHEDULE_KEY);
        updateAlwaysOnSummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAlwaysOnSummary();
    }

    private void updateAlwaysOnSummary() {
        if (mAODPref == null) return;
        int mode = Settings.Secure.getIntForUser(getActivity().getContentResolver(),
                Settings.Secure.DOZE_ALWAYS_ON_AUTO_MODE, 0, UserHandle.USER_CURRENT);
        switch (mode) {
            default:
            case MODE_DISABLED:
                mAODPref.setSummary(R.string.disabled);
                break;
            case MODE_NIGHT:
                mAODPref.setSummary(R.string.night_display_auto_mode_twilight);
                break;
            case MODE_TIME:
                mAODPref.setSummary(R.string.night_display_auto_mode_custom);
                break;
            case MODE_MIXED_SUNSET:
                mAODPref.setSummary(R.string.always_on_display_schedule_mixed_sunset);
                break;
            case MODE_MIXED_SUNRISE:
                mAODPref.setSummary(R.string.always_on_display_schedule_mixed_sunrise);
                break;
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mFingerprintVib) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(resolver,
                    Settings.System.FINGERPRINT_SUCCESS_VIB, value ? 1 : 0);
            return true;
        } else if (preference == mFingerprintErrorVib) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(resolver,
                    Settings.System.FINGERPRINT_ERROR_VIB, value ? 1 : 0);
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.SUPERIOR;
    }

    /**
     * For Search.
     */

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.superior_settings_lockscreen) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);
                    FingerprintManager mFingerprintManager = (FingerprintManager)
                            context.getSystemService(Context.FINGERPRINT_SERVICE);
                    if (mFingerprintManager == null || !mFingerprintManager.isHardwareDetected()) {
                        keys.add(FINGERPRINT_VIB);
                        keys.add(FINGERPRINT_ERROR_VIB);
                    }

                    if (!FodUtils.hasFodSupport(context)) {
                        keys.add(FOD_ICON_PICKER_CATEGORY);
                    }

                    if (!Utils.isBlurSupported()) {
                        keys.add(KEY_LOCKSCREEN_BLUR);
                    }

                    return keys;
                }
            };
}