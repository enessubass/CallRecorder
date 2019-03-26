package net.synapticweb.callrecorder.recorder;

import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

//Bazat pe codul de aici: https://gist.github.com/nieldeokar/fbfcae08e5612bd7cc36a30254694ee3
//Pornind de aici: https://stackoverflow.com/questions/8499042/android-audiorecord-example
//și aici: https://stackoverflow.com/questions/32218360/android-audio-record-to-wav
//https://www.androidcookbook.info/android-media/raw-audio-recording-with-audiorecord.html
class RecordingThreadAac extends RecordingThread implements Runnable {
    private static final int SAMPLE_RATE_INDEX = 4;
    private final int bitRate;
    private final MediaCodec mediaCodec;
    private final OutputStream outputStream;

    RecordingThreadAac(File audioFile, String format, String mode) throws RuntimeException {
        super(mode);
        switch (format) {
            case Recorder.AAC_HIGH_FORMAT: bitRate = 128000;
                break;
            case Recorder.AAC_MEDIUM_FORMAT: bitRate = 64000;
                break;
            case Recorder.AAC_BASIC_FORMAT: bitRate = 32000;
                break;
            default:bitRate = 64000;
        }

        mediaCodec = createMediaCodec(bufferSize);
        try {
            outputStream = new FileOutputStream(audioFile);
        }
        catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        mediaCodec.start();
    }

    @Override
    public void run() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        ByteBuffer[] codecInputBuffers = mediaCodec.getInputBuffers();
        ByteBuffer[] codecOutputBuffers = mediaCodec.getOutputBuffers();

        try {
            while (!Thread.interrupted()) {
                boolean success = handleCodecInput(audioRecord, mediaCodec, codecInputBuffers, Thread.currentThread().isAlive());
                if (success)
                    handleCodecOutput(mediaCodec, codecOutputBuffers, bufferInfo, outputStream);
            }
        }
        finally {
            disposeAudioRecord();
            mediaCodec.stop();
            mediaCodec.release();
            try {
                outputStream.close();
            } catch (IOException e) {
               Log.wtf(TAG, "Error while closing the recording filestream: " + e.getMessage());
            }
        }
    }


    private MediaCodec createMediaCodec(int bufferSize)  {
        MediaCodec mediaCodec = null;
        MediaFormat mediaFormat = new MediaFormat();

        mediaFormat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channels);
        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);

        try {
            mediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (Exception e) {
            if(mediaCodec != null)
                mediaCodec.release();
            throw new RuntimeException("Cannot create mediacodec: " + e.getMessage());
        }
        return mediaCodec;
    }

    private boolean handleCodecInput(AudioRecord audioRecord,
                                     MediaCodec mediaCodec, ByteBuffer[] codecInputBuffers,
                                     boolean running) {
        byte[] audioRecordData = new byte[bufferSize];
        int length = audioRecord.read(audioRecordData, 0, audioRecordData.length);

        if (length == AudioRecord.ERROR_BAD_VALUE ||
                length == AudioRecord.ERROR_INVALID_OPERATION ||
                length != bufferSize) {

            if (length != bufferSize) {
                Log.wtf(TAG, "length != BufferSize");
                return false;
            }
        }

        int codecInputBufferIndex = mediaCodec.dequeueInputBuffer(10 * 1000);

        if (codecInputBufferIndex >= 0) {
            ByteBuffer codecBuffer = codecInputBuffers[codecInputBufferIndex];
            codecBuffer.clear();
            codecBuffer.put(audioRecordData);
            mediaCodec.queueInputBuffer(codecInputBufferIndex, 0, length, 0, running ? 0 : MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }
        return true;
    }

    private void handleCodecOutput(MediaCodec mediaCodec,
                                   ByteBuffer[] codecOutputBuffers,
                                   MediaCodec.BufferInfo bufferInfo,
                                   OutputStream outputStream)
             {
        int codecOutputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);

        while (codecOutputBufferIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
            if (codecOutputBufferIndex >= 0) {
                ByteBuffer encoderOutputBuffer = codecOutputBuffers[codecOutputBufferIndex];

                encoderOutputBuffer.position(bufferInfo.offset);
                encoderOutputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                    byte[] header = createAdtsHeader(bufferInfo.size - bufferInfo.offset);

                    try {
                        outputStream.write(header);

                        byte[] data = new byte[encoderOutputBuffer.remaining()];
                        encoderOutputBuffer.get(data);
                        outputStream.write(data);
                    }
                    catch (IOException e) {
                        Log.wtf(TAG, "Error while writing recording file: " + e.getMessage());
                    }
                }

                encoderOutputBuffer.clear();

                mediaCodec.releaseOutputBuffer(codecOutputBufferIndex, false);
            } else if (codecOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = mediaCodec.getOutputBuffers();
            }
            codecOutputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        }
    }

    private byte[] createAdtsHeader(int length) {
        int frameLength = length + 7;
        byte[] adtsHeader = new byte[7];

        adtsHeader[0] = (byte) 0xFF; // Sync Word
        adtsHeader[1] = (byte) 0xF1; // MPEG-4, Layer (0), No CRC
        adtsHeader[2] = (byte) ((MediaCodecInfo.CodecProfileLevel.AACObjectLC - 1) << 6);
        adtsHeader[2] |= (((byte) SAMPLE_RATE_INDEX) << 2);
        adtsHeader[2] |= (((byte) channels) >> 2);
        adtsHeader[3] = (byte) (((channels & 3) << 6) | ((frameLength >> 11) & 0x03));
        adtsHeader[4] = (byte) ((frameLength >> 3) & 0xFF);
        adtsHeader[5] = (byte) (((frameLength & 0x07) << 5) | 0x1f);
        adtsHeader[6] = (byte) 0xFC;

        return adtsHeader;
    }

}