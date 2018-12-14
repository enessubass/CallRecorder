package net.synapticweb.callrecorder.data;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import net.synapticweb.callrecorder.contactdetail.ExportAsyncTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class Recording implements Parcelable {
    private long id;
    private String path;
    private Boolean incoming;
    private Long startTimestamp, endTimestamp;

    public Recording(long id, String path, Boolean incoming, Long startTimestamp, Long endTimestamp) {
        this.id = id;
        this.path = path;
        this.incoming = incoming;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
    }

    public String getDate() {
        Calendar recordingCal = Calendar.getInstance();
        recordingCal.setTimeInMillis(startTimestamp);
        if(recordingCal.get(Calendar.YEAR) < Calendar.getInstance().get(Calendar.YEAR) )
            return new SimpleDateFormat("d MMM ''yy", Locale.US).format(new Date(startTimestamp)); //22 Aug '17
        else
            return new SimpleDateFormat("d MMM", Locale.US).format(new Date(startTimestamp));
    }

    public String getTime() {
        return new SimpleDateFormat("h:mm a", Locale.US).format(new Date(startTimestamp)); //3:45 PM
    }

    public void delete(Context context) throws SQLException, SecurityException
    {
        CallRecorderDbHelper mDbHelper = new CallRecorderDbHelper(context);
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if((db.delete(RecordingsContract.Recordings.TABLE_NAME,
                RecordingsContract.Recordings._ID + "=" + getId(), null)) == 0)
            throw new SQLException("The Recording row was not deleted");

        new File(path).delete();
    }

    public void export(String folderPath, ExportAsyncTask asyncTask, long totalSize, String phoneNumber) throws IOException {
        String timeSeparator = (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) ? "_" : ":";
        String fileName = phoneNumber + " - " + new SimpleDateFormat("d_MMM_yyyy_HH" + timeSeparator
                + "mm" + timeSeparator + "ss", Locale.US).
                format(new Date(startTimestamp)) + ".amr";
        InputStream in = new FileInputStream(path);
        OutputStream out = new FileOutputStream(new File(folderPath, fileName));

        byte[] buffer = new byte[1048576]; //dacă folosesc 1024 merge foarte încet
        int read;
        while ((read = in.read(buffer)) != -1) {
            asyncTask.alreadyCopied += read;
            out.write(buffer, 0, read);
            asyncTask.callPublishProgress(Math.round(100 * asyncTask.alreadyCopied / totalSize));
            if(asyncTask.isCancelled())
                break;
        }
        in.close();
        out.flush();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isIncoming() {
        return incoming;
    }

    public void setIncoming(boolean incoming) {
        this.incoming = incoming;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(this.id);
        dest.writeString(this.path);
        dest.writeValue(this.incoming);
        dest.writeValue(this.startTimestamp);
        dest.writeValue(this.endTimestamp);
    }

    private Recording(Parcel in) {
        this.id = in.readLong();
        this.path = in.readString();
        this.incoming = (Boolean) in.readValue(Boolean.class.getClassLoader());
        this.startTimestamp = (Long) in.readValue(Long.class.getClassLoader());
        this.endTimestamp = (Long) in.readValue(Long.class.getClassLoader());
    }

    public static final Parcelable.Creator<Recording> CREATOR = new Parcelable.Creator<Recording>() {
        @Override
        public Recording createFromParcel(Parcel source) {
            return new Recording(source);
        }

        @Override
        public Recording[] newArray(int size) {
            return new Recording[size];
        }
    };
}