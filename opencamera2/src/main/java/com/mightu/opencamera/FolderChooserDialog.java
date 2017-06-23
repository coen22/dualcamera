package com.mightu.opencamera;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import net.sourceforge.opencamera.MyDebug;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FolderChooserDialog extends DialogFragment {
	private static final String TAG = "FolderChooserFragment";

	private File current_folder = null;
	private AlertDialog folder_dialog = null;
	private ListView list = null;

	private class FileWrapper implements Comparable<FileWrapper> {
		private File file = null;
		private boolean is_parent = false;

		FileWrapper(File file, boolean is_parent) {
			this.file = file;
			this.is_parent = is_parent;
		}

		@Override
		public String toString() {
			if( this.is_parent )
				return getResources().getString(R.string.parent_folder);
			return file.getName();
		}

		@SuppressLint("DefaultLocale")
		@Override
		public int compareTo(FileWrapper o) {
			if( this.is_parent )
				return -1;
			else if( o.isParent() )
				return 1;
			return this.file.getName().toLowerCase().compareTo(o.getFile().getName().toLowerCase());
		}

		File getFile() {
			return file;
		}

		private boolean isParent() {
			return is_parent;
		}
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		if( MyDebug.LOG )
			Log.d(TAG, "onCreateDialog");
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
		String folder_name = sharedPreferences.getString("preference_save_location", "OpenCamera");
		if( MyDebug.LOG )
			Log.d(TAG, "folder_name: " + folder_name);
		File new_folder = MainActivity.getImageFolder(folder_name);
		if( MyDebug.LOG )
			Log.d(TAG, "start in folder: " + new_folder);

		list = new ListView(getActivity());
		list.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if( MyDebug.LOG )
					Log.d(TAG, "onItemClick: " + position);
				FileWrapper file_wrapper = (FileWrapper) parent.getItemAtPosition(position);
				if( MyDebug.LOG )
					Log.d(TAG, "clicked: " + file_wrapper.toString());
				File file = file_wrapper.getFile();
				if( MyDebug.LOG )
					Log.d(TAG, "file: " + file.toString());
				refreshList(file);
			}
		});
		folder_dialog = new AlertDialog.Builder(getActivity())
				//.setIcon(R.drawable.alert_dialog_icon)
				.setView(list)
				.setPositiveButton(R.string.use_folder, null) // we set the listener in onShowListener, so we can prevent the dialog from closing (if chosen folder isn't writable)
				.setNeutralButton(R.string.new_folder, null) // we set the listener in onShowListener, so we can prevent the dialog from closing
				.setNegativeButton(android.R.string.cancel, null)
				.create();
		folder_dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override
			public void onShow(DialogInterface dialog_interface) {
				Button b_positive = folder_dialog.getButton(AlertDialog.BUTTON_POSITIVE);
				b_positive.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						if( MyDebug.LOG )
							Log.d(TAG, "choose folder: " + current_folder.toString());
						if( useFolder() ) {
							folder_dialog.dismiss();
						}
					}
				});
				Button b_neutral = folder_dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
				b_neutral.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						if( MyDebug.LOG )
							Log.d(TAG, "new folder in: " + current_folder.toString());
						newFolder();
					}
				});
			}
		});

		if( !new_folder.exists() ) {
			if( MyDebug.LOG )
				Log.d(TAG, "create new folder" + new_folder);
			if( !new_folder.mkdirs() ) {
				if( MyDebug.LOG )
					Log.d(TAG, "failed to create new folder");
				// don't do anything yet, this is handled below
			}
		}
		refreshList(new_folder);
		if( current_folder == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "failed to read folder");
			// note that we reset to DCIM rather than DCIM/OpenCamera, just to increase likelihood of getting back to a valid state
			refreshList(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));
			if( current_folder == null ) {
				if( MyDebug.LOG )
					Log.d(TAG, "can't even read DCIM?!");
			}
		}
		return folder_dialog;
	}

	private void refreshList(File new_folder) {
		if( MyDebug.LOG )
			Log.d(TAG, "refreshList: " + new_folder);
		if( new_folder == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "refreshList: null folder");
			return;
		}
		File [] files = null;
		// try/catch just in case?
		try {
			files = new_folder.listFiles();
		}
		catch(Exception e) {
			if( MyDebug.LOG )
				Log.d(TAG, "exception reading folder");
			e.printStackTrace();
		}
		if( files == null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "couldn't read folder");
			String toast_message = getResources().getString(R.string.cant_access_folder) + ":\n" + new_folder.getAbsolutePath();
			Toast.makeText(getActivity(), toast_message, Toast.LENGTH_SHORT).show();
			return;
		}
		List<FileWrapper> listed_files = new ArrayList<FileWrapper>();
		if( new_folder.getParentFile() != null )
			listed_files.add(new FileWrapper(new_folder.getParentFile(), true));
		for(int i=0;i<files.length;i++) {
			File file = files[i];
			if( file.isDirectory() ) {
				listed_files.add(new FileWrapper(file, false));
			}
		}
		Collections.sort(listed_files);

		ArrayAdapter<FileWrapper> adapter = new ArrayAdapter<FileWrapper>(this.getActivity(), android.R.layout.simple_list_item_1, listed_files);
		list.setAdapter(adapter);

		this.current_folder = new_folder;
		//dialog.setTitle(current_folder.getName());
		folder_dialog.setTitle(current_folder.getAbsolutePath());
	}

	private boolean canWrite() {
		try {
			if( this.current_folder != null && this.current_folder.canWrite() )
				return true;
		}
		catch(Exception e) {
		}
		return false;
	}

	private boolean useFolder() {
		if( MyDebug.LOG )
			Log.i(TAG, "useFolder");
		if( current_folder == null )
			return false;
		if( canWrite() ) {
			File base_folder = MainActivity.getBaseFolder();
			String new_save_location = current_folder.getAbsolutePath();
			if( current_folder.getParentFile() != null && current_folder.getParentFile().equals(base_folder) ) {
				if( MyDebug.LOG )
					Log.i(TAG, "parent folder is base folder");
				//2015-4-14 16:07:57， mightu： 这里逻辑是如果是 DCIM 下级目录； 估计是为了只显示 OpenCamera 名， 但会造成我 xml 保存路径错误
				//不懂， 那原程序保存 img 怎么不出错？
				new_save_location = current_folder.getName();
			}
			if( MyDebug.LOG )
				Log.i(TAG, "new_save_location: " + new_save_location);
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString("preference_save_location", new_save_location);
			editor.apply();
			return true;
		}
		else {
			Toast.makeText(getActivity(), R.string.cant_write_folder, Toast.LENGTH_SHORT).show();
		}
		return false;
	}

	private void newFolder() {
		if( MyDebug.LOG )
			Log.i(TAG, "newFolder");
		if( current_folder == null )
			return;
		if( canWrite() ) {
			final EditText edit_text = new EditText(getActivity());
			edit_text.setSingleLine();
			InputFilter filter = new InputFilter() {
				// whilst Android seems to allow any characters on internal memory, SD cards are typically formatted with FAT32
				String disallowed = "|\\?*<\":>";
				public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
					for(int i=start;i<end;i++) {
						if( disallowed.indexOf( source.charAt(i) ) != -1 ) {
							return "";
						}
					}
					return null;
				}
			};
			edit_text.setFilters(new InputFilter[]{filter});

			Dialog dialog = new AlertDialog.Builder(getActivity())
					//.setIcon(R.drawable.alert_dialog_icon)
					.setTitle(R.string.enter_new_folder)
					.setView(edit_text)
					.setPositiveButton(android.R.string.ok, new Dialog.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							if( edit_text.getText().length() == 0 ) {
								// do nothing
							}
							else {
								try {
									String new_folder_name = current_folder.getAbsolutePath() + File.separator + edit_text.getText().toString();
									if( MyDebug.LOG )
										Log.d(TAG, "create new folder: " + new_folder_name);
									File new_folder = new File(new_folder_name);
									if( new_folder.exists() ) {
										if( MyDebug.LOG )
											Log.d(TAG, "folder already exists");
										Toast.makeText(getActivity(), R.string.folder_exists, Toast.LENGTH_SHORT).show();
									}
									else if( new_folder.mkdirs() ) {
										if( MyDebug.LOG )
											Log.d(TAG, "created new folder");
										refreshList(current_folder);
									}
									else {
										if( MyDebug.LOG )
											Log.d(TAG, "failed to create new folder");
										Toast.makeText(getActivity(), R.string.failed_create_folder, Toast.LENGTH_SHORT).show();
									}
								}
								catch(Exception e) {
									if( MyDebug.LOG )
										Log.d(TAG, "exception trying to create new folder");
									e.printStackTrace();
									Toast.makeText(getActivity(), R.string.failed_create_folder, Toast.LENGTH_SHORT).show();
								}
							}
						}
					})
					.setNegativeButton(android.R.string.cancel, null)
					.create();
			dialog.show();
		}
		else {
			Toast.makeText(getActivity(), R.string.cant_write_folder, Toast.LENGTH_SHORT).show();
		}
	}


	@Override
	public void onResume() {
		super.onResume();
		// refresh in case files have changed
		refreshList(current_folder);
	}

	// for testing:

	public File getCurrentFolder() {
		return current_folder;
	}
}