package be.lukin.android.lang;

import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.app.ActionBar;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import be.lukin.android.lang.Constants.State;
import be.lukin.android.lang.provider.Phrase;

import java.util.ArrayList;
import java.util.List;


public class DefaultActivity extends AbstractRecognizerActivity {

	private State mState = State.INIT;

	private Resources mRes;
	private SharedPreferences mPrefs;

	private static String mCurrentSortOrder;

	private MicButton mButtonMicrophone;

	private AudioCue mAudioCue;

	private ActionBar mActionBar;

	private SpeechRecognizer mSr;

	private TextView mTvPhrase;
	private TextView mTvResult;

	private final List<PhraseItem> mPhrases = getPhrases();

	private static final Uri CONTENT_URI = Phrase.Columns.CONTENT_URI;


	private class PhraseItem {
		private final String mText;
		private final String mLang;
		public PhraseItem(String text, String lang) {
			mText = text;
			mLang = lang;
		}
		public String getText() {
			return mText;
		}
		public String getLang() {
			return mLang;
		}
	}


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);


		mRes = getResources();
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		mTvPhrase = (TextView) findViewById(R.id.tvPhrase);
		mTvResult = (TextView) findViewById(R.id.tvResult);
		mButtonMicrophone = (MicButton) findViewById(R.id.buttonMicrophone);

		mActionBar = getActionBar();
		mActionBar.setHomeButtonEnabled(false);
	}


	private List<PhraseItem> getPhrases() {
		List<PhraseItem> phrases = new ArrayList<PhraseItem>();
		phrases.add(new PhraseItem("Vamos a la playa", "es-MX"));
		phrases.add(new PhraseItem("Cogito ergo sum", "Latin"));
		phrases.add(new PhraseItem("Белеет парус одинокий. В тумане моря голубом!", "ru-RU"));
		phrases.add(new PhraseItem("How much wood would a woodchuck chuck if a woodchuck could chuck wood?", "en-US"));
		return phrases;
	}


	/**
	 * We initialize the speech recognizer here, assuming that the configuration
	 * changed after onStop. That is why onStop destroys the recognizer.
	 */
	@Override
	public void onStart() {
		super.onStart();

		if (mPrefs.getBoolean(getString(R.string.keyAudioCues), mRes.getBoolean(R.bool.defaultAudioCues))) {
			mAudioCue = new AudioCue(this);
		} else {
			mAudioCue = null;
		}

		ComponentName serviceComponent = getServiceComponent();

		if (serviceComponent == null) {
			toast(getString(R.string.errorNoDefaultRecognizer));
			//TODO: goToStore();
		} else {
			Log.i("Starting service: " + serviceComponent);
			mSr = SpeechRecognizer.createSpeechRecognizer(this, serviceComponent);
			if (mSr == null) {
				toast(getString(R.string.errorNoDefaultRecognizer));
			} else {
				setUpRecognizerGui(mSr);
			}
		}
	}


	@Override
	public void onResume() {
		super.onResume();
	}


	@Override
	public void onStop() {
		super.onStop();

		if (mSr != null) {
			mSr.cancel(); // TODO: do we need this, we do destroy anyway?
			mSr.destroy();
		}

		SharedPreferences.Editor editor = mPrefs.edit();
		editor.putString(getString(R.string.prefCurrentSortOrder), mCurrentSortOrder);
		editor.commit();
	}


	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mSr != null) {
			mSr.destroy();
			mSr = null;
		}
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);

		// Indicate the current sort order by checking the corresponding radio button
		int id = mPrefs.getInt(getString(R.string.prefCurrentSortOrderMenu), R.id.menuMainSortByTimestamp);
		MenuItem menuItem = menu.findItem(id);
		menuItem.setChecked(true);
		return true;
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		// TODO: move this to the phrases activity
		case R.id.menuMainSortByTimestamp:
			//sort(item, SORT_ORDER_TIMESTAMP);
			return true;
		case R.id.menuMainPhrases:
			startActivity(new Intent(this, PhrasesActivity.class));
			return true;
		case R.id.menuMainSettings:
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}


	private void sort(MenuItem item, String sortOrder) {
		//startQuery(sortOrder);
		item.setChecked(true);
		// Save the ID of the selected item.
		// TODO: ideally this should be done in onDestory
		SharedPreferences.Editor editor = mPrefs.edit();
		editor.putInt(getString(R.string.prefCurrentSortOrderMenu), item.getItemId());
		editor.commit();
	}


	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.cm_main, menu);
	}


	private Intent createRecognizerIntent(String langSource) {
		Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
		intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getApplicationContext().getPackageName());
		intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
		intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langSource);
		if (mPrefs.getBoolean(getString(R.string.keyMaxOneResult), mRes.getBoolean(R.bool.defaultMaxOneResult))) { 
			intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
		}
		return intent;
	}


	private void setUpRecognizerGui(final SpeechRecognizer sr) {
		mButtonMicrophone.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (mState == State.INIT || mState == State.ERROR) {
					PhraseItem phrase = getPhrase();
					setUiInput(phrase.getText());
					if (mAudioCue != null) {
						mAudioCue.playStartSoundAndSleep();
					}
					startListening(sr, phrase.getText(), phrase.getLang());
				}
				else if (mState == State.LISTENING) {
					sr.stopListening();
				} else {
					// TODO: bad state to press the button
				}
			}
		});

		LinearLayout llMicrophone = (LinearLayout) findViewById(R.id.llMicrophone);
		llMicrophone.setVisibility(View.VISIBLE);
		llMicrophone.setEnabled(true);
	}


	/**
	 * Look up the default recognizer service in the preferences.
	 * If the default have not been set then set the first available
	 * recognizer as the default. If no recognizer is installed then
	 * return null.
	 */
	private ComponentName getServiceComponent() {
		String pkg = mPrefs.getString(getString(R.string.keyService), null);
		String cls = mPrefs.getString(getString(R.string.prefRecognizerServiceCls), null);
		if (pkg == null || cls == null) {
			List<ResolveInfo> services = getPackageManager().queryIntentServices(
					new Intent(RecognitionService.SERVICE_INTERFACE), 0);
			if (services.isEmpty()) {
				return null;
			}
			ResolveInfo ri = services.iterator().next();
			pkg = ri.serviceInfo.packageName;
			cls = ri.serviceInfo.name;
			SharedPreferences.Editor editor = mPrefs.edit();
			editor.putString(getString(R.string.keyService), pkg);
			editor.putString(getString(R.string.prefRecognizerServiceCls), cls);
			editor.commit();
		}
		return new ComponentName(pkg, cls);
	}


	private void addEntry(String text, String lang, int dist, String result) {
		ContentValues values = new ContentValues();
		values.put(Phrase.Columns.TEXT, text);
		values.put(Phrase.Columns.LANG, lang);
		values.put(Phrase.Columns.DIST, dist);
		values.put(Phrase.Columns.RESULT, result);
		insert(CONTENT_URI, values);
	}


	// TODO: dummy
	private PhraseItem getPhrase() {
		return mPhrases.get(getRandom(mPhrases.size()-1));
	}

	private int getRandom(int max) {
		return (int)(Math.random() * ((max) + 1));
	}


	private void setUiInput(String text) {
		mTvPhrase.setText(text);
		mTvResult.setText("");
	}


	private void setUiResult(String resultText) {
		mTvResult.setText(resultText);
	}


	private void startListening(final SpeechRecognizer sr, String phrase, String lang) {

		final String mPhrase = phrase;
		final String mLang = lang;
		Intent intentRecognizer = createRecognizerIntent(lang);

		sr.setRecognitionListener(new RecognitionListener() {

			@Override
			public void onBeginningOfSpeech() {
				mState = State.LISTENING;
			}

			@Override
			public void onBufferReceived(byte[] buffer) {
				// TODO maybe show buffer waveform
			}

			@Override
			public void onEndOfSpeech() {
				mState = State.TRANSCRIBING;
				mButtonMicrophone.setState(mState);
				if (mAudioCue != null) {
					mAudioCue.playStopSound();
				}
			}

			@Override
			public void onError(int error) {
				mState = State.ERROR;
				mButtonMicrophone.setState(mState);
				if (mAudioCue != null) {
					mAudioCue.playErrorSound();
				}
				switch (error) {
				case SpeechRecognizer.ERROR_AUDIO:
					showErrorDialog(R.string.errorResultAudioError);
					break;
				case SpeechRecognizer.ERROR_CLIENT:
					showErrorDialog(R.string.errorResultClientError);
					break;
				case SpeechRecognizer.ERROR_NETWORK:
					showErrorDialog(R.string.errorResultNetworkError);
					break;
				case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
					showErrorDialog(R.string.errorResultNetworkError);
					break;
				case SpeechRecognizer.ERROR_SERVER:
					showErrorDialog(R.string.errorResultServerError);
					break;
				case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
					showErrorDialog(R.string.errorResultServerError);
					break;
				case SpeechRecognizer.ERROR_NO_MATCH:
					showErrorDialog(R.string.errorResultNoMatch);
					break;
				case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
					showErrorDialog(R.string.errorResultNoMatch);
					break;
				case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
					// This is programmer error.
					break;
				default:
					break;
				}
			}

			@Override
			public void onEvent(int eventType, Bundle params) {
				// TODO ???
			}

			@Override
			public void onPartialResults(Bundle partialResults) {
				// ignore
			}

			@Override
			public void onReadyForSpeech(Bundle params) {
				mState = State.RECORDING;
				mButtonMicrophone.setState(mState);
			}

			@Override
			public void onResults(Bundle results) {
				ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
				// TODO: confidence scores support is only in API 14
				mState = State.INIT;
				mButtonMicrophone.setState(mState);
				if (matches.isEmpty()) {
					toast("ERROR: No results"); // TODO
				} else {
					// TODO: we just take the first result for the time being
					String result = matches.iterator().next();
					setUiResult(result);
					addEntry(mPhrase, mLang, 123, result);
				}
			}

			@Override
			public void onRmsChanged(float rmsdB) {
				mButtonMicrophone.setVolumeLevel(rmsdB);
			}
		});
		sr.startListening(intentRecognizer);
	}

}
