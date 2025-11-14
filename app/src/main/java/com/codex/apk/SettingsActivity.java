package com.codex.apk;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Context;
import android.util.AttributeSet;
import java.util.List;
import com.codex.apk.ai.AIModel;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class SettingsActivity extends AppCompatActivity {
	
	private MaterialToolbar toolbar;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Set up theme based on user preferences
		ThemeManager.setupTheme(this);
		
		try {
			setContentView(R.layout.settings);
			
			// Initialize toolbar
			toolbar = findViewById(R.id.toolbar);
			if (toolbar != null) {
				setSupportActionBar(toolbar);
				if (getSupportActionBar() != null) {
					getSupportActionBar().setTitle("Settings");
					getSupportActionBar().setDisplayHomeAsUpEnabled(true);
				}
			}
			
			// Settings UI is directly in the layout, no fragment needed
			// Initialize settings controls
			initializeSettings();
		} catch (Exception e) {
			Toast.makeText(this, "Error loading settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
			finish();
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private void initializeSettings() {
		// Initialize settings controls from the layout
		LinearLayout themeSelectorLayout = findViewById(R.id.layout_theme_selector);
		TextView selectedThemeText = findViewById(R.id.text_selected_theme);
		com.google.android.material.card.MaterialCardView modelsCard = findViewById(R.id.card_models);
		com.google.android.material.materialswitch.MaterialSwitch wrapSwitch = findViewById(R.id.switch_wrap);
		com.google.android.material.materialswitch.MaterialSwitch readOnlySwitch = findViewById(R.id.switch_read_only);
		com.google.android.material.slider.Slider fontSizeSlider = findViewById(R.id.slider_font_size);

		// Load saved settings
		SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
		SharedPreferences defaultPrefs = getPreferences(this);
		String savedTheme = defaultPrefs.getString("app_theme", "light");
		boolean wrapEnabled = isDefaultWordWrap(this);
		boolean readOnlyEnabled = isDefaultReadOnly(this);

		if (selectedThemeText != null) selectedThemeText.setText(getThemeDisplayName(savedTheme));
		if (wrapSwitch != null) wrapSwitch.setChecked(wrapEnabled);
		if (readOnlySwitch != null) readOnlySwitch.setChecked(readOnlyEnabled);
		if (fontSizeSlider != null) {
			fontSizeSlider.setValue(getFontSize(this));
		}

		// Clicks
		if (themeSelectorLayout != null) themeSelectorLayout.setOnClickListener(v -> showThemeSelector());
		if (modelsCard != null) modelsCard.setOnClickListener(v -> {
			startActivity(new Intent(this, ModelsActivity.class));
		});
		findViewById(R.id.about_card).setOnClickListener(v -> {
            startActivity(new Intent(this, AboutActivity.class));
        });

		// Switch Listeners
		if (wrapSwitch != null) {
			wrapSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
				getPreferences(this).edit().putBoolean("default_word_wrap", isChecked).apply();
			});
		}
		if (readOnlySwitch != null) {
			readOnlySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
				getPreferences(this).edit().putBoolean("default_read_only", isChecked).apply();
			});
		}


		if (fontSizeSlider != null) {
			fontSizeSlider.addOnChangeListener((slider, value, fromUser) -> {
				getPreferences(this).edit().putInt("font_size_int", (int) value).apply();
			});
		}
	}

	private void setupDebouncedSaver(com.google.android.material.textfield.TextInputEditText editText, java.util.function.Consumer<String> onSave) {
		if (editText == null) return;
		editText.addTextChangedListener(new android.text.TextWatcher() {
			private android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
			private Runnable saveRunnable;
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			public void onTextChanged(CharSequence s, int start, int before, int count) {}
			public void afterTextChanged(android.text.Editable s) {
				if (saveRunnable != null) handler.removeCallbacks(saveRunnable);
				saveRunnable = () -> onSave.accept(s.toString().trim());
				handler.postDelayed(saveRunnable, 700);
			}
		});
	}

	private void showThemeSelector() {
		String currentTheme = getPreferences(this).getString("app_theme", "light");
		String[] themeEntries = getResources().getStringArray(R.array.theme_entries);
		String[] themeValues = getResources().getStringArray(R.array.theme_values);

		int currentIndex = 0;
		for (int i = 0; i < themeValues.length; i++) {
			if (themeValues[i].equals(currentTheme)) {
				currentIndex = i;
				break;
			}
		}

		new MaterialAlertDialogBuilder(this, R.style.AlertDialogCustom)
			.setTitle("Select Theme")
			.setSingleChoiceItems(themeEntries, currentIndex, (dialog, which) -> {
				String selectedTheme = themeValues[which];
				String selectedThemeDisplay = themeEntries[which];

				TextView selectedThemeText = findViewById(R.id.text_selected_theme);
				if (selectedThemeText != null) {
					selectedThemeText.setText(selectedThemeDisplay);
				}

				// Save the theme preference using default preferences
				getPreferences(this)
					.edit()
					.putString("app_theme", selectedTheme)
					.apply();

				// Apply theme immediately
				ThemeManager.switchTheme(this, selectedTheme);

				dialog.dismiss();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private String getThemeDisplayName(String themeValue) {
		String[] themeEntries = getResources().getStringArray(R.array.theme_entries);
		String[] themeValues = getResources().getStringArray(R.array.theme_values);

		for (int i = 0; i < themeValues.length; i++) {
			if (themeValues[i].equals(themeValue)) {
				return themeEntries[i];
			}
		}
		return "Light"; // Default
	}
	
	// Helper method to get preferences
	public static SharedPreferences getPreferences(android.content.Context context) {
		return context.getSharedPreferences("settings", MODE_PRIVATE);
	}
	
	public static String getAppTheme(android.content.Context context) {
		return getPreferences(context).getString("app_theme", "light");
	}
	
	public static int getFontSize(android.content.Context context) {
		return getPreferences(context).getInt("font_size_int", 14);
	}
	
	
	public static String getFontFamily(Context context) {
		return getPreferences(context).getString("font_family", "poppins");
	}
	
	public static String getFontWeight(Context context) {
		return getPreferences(context).getString("font_weight", "reg");
	}
	
	public static String getFontFileName(Context context) {
		String family = getFontFamily(context);
		String weight = getFontWeight(context);
		
		switch (family) {
			case "poppins":
			return "poppins_" + weight + ".ttf";
			case "firacode":
			return "firacode_" + (weight.equals("reg") ? "regular" : 
			weight.equals("med") ? "medium" : "semibold") + ".ttf";
			case "jetbrainsmono":
			return "jetbrainsmono_" + (weight.equals("reg") ? "regular" : 
			weight.equals("med") ? "medium" : "semibold") + ".ttf";
			default:
			return "poppins_reg.ttf";
		}
	}
	
	public static String getQwenApiToken(Context context) {
		return getPreferences(context).getString("qwen_api_token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjhiYjQ1NjVmLTk3NjUtNDQwNi04OWQ5LTI3NmExMTIxMjBkNiIsImxhc3RfcGFzc3dvcmRfY2hhbmdlIjoxNzUwNjYwODczLCJleHAiOjE3NTU4NDg1NDh9.pb0IybY9tQkriqMUOos72FKtZM3G4p1_aDzwqqh5zX4");
	}

	// Custom prompt settings
	public static String getCustomFileOpsPrompt(Context context) {
		return getPreferences(context).getString("custom_fileops_prompt", "");
	}
	public static void setCustomFileOpsPrompt(Context context, String prompt) {
		getPreferences(context).edit().putString("custom_fileops_prompt", prompt != null ? prompt : "").apply();
	}
	public static String getCustomGeneralPrompt(Context context) {
		return getPreferences(context).getString("custom_general_prompt", "");
	}
	public static void setCustomGeneralPrompt(Context context, String prompt) {
		getPreferences(context).edit().putString("custom_general_prompt", prompt != null ? prompt : "").apply();
	}

	public static boolean isLineNumbersEnabled(android.content.Context context) {
		return getPreferences(context).getBoolean("line_numbers", true);
	}
	
	public static boolean isAiHistoryEnabled(android.content.Context context) {
		return getPreferences(context).getBoolean("ai_history", true);
	}

    public static boolean isDefaultReadOnly(android.content.Context context) {
        return getPreferences(context).getBoolean("default_read_only", false);
    }

    public static boolean isDefaultWordWrap(android.content.Context context) {
        return getPreferences(context).getBoolean("default_word_wrap", true);
    }

    public static boolean isAgentModeEnabled(android.content.Context context) {
        return getPreferences(context).getBoolean("agent_mode_enabled", true);
    }
}