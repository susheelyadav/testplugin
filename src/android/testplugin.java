package testplugin;

import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL;
import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS;

import android.media.MediaMetadataRetriever;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.ExecuteCallback;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.arthenica.mobileffmpeg.LogCallback;
import com.arthenica.mobileffmpeg.LogMessage;
import com.arthenica.mobileffmpeg.Statistics;
import com.arthenica.mobileffmpeg.StatisticsCallback;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class testplugin extends CordovaPlugin {

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if ("mergeAudioFiles".equals(action)) {
            final JSONArray fileURIs = args.getJSONArray(0); // Assuming the first argument is the array of file URIs
            final String[] audioFiles = new String[fileURIs.length()];

            for (int i = 0; i < fileURIs.length(); i++) {
                audioFiles[i] = fileURIs.getString(i);
            }

            // Generate a unique output file path or use a predetermined path
            String outputFile = generateOutputFilePath();
            final boolean parallelMixing = args.getBoolean(1);

            try {
                mergeFile(audioFiles, outputFile, parallelMixing, callbackContext);
                PluginResult pluginResult = new  PluginResult(PluginResult.Status.NO_RESULT);
                pluginResult.setKeepCallback(true);
                callbackContext.sendPluginResult(pluginResult);
            } catch (Exception e) {
                // Handle exceptions and call the error callback on the main thread
                final String errorMessage = e.toString();
                callbackContext.error("Exception occurred: " + errorMessage);
            }


            return true;
        }

        // Other actions can be handled here

        return false; // Returning false results in an "Invalid Action" error.
    }


    private String generateOutputFilePath() {
        // Implementation depends on your file naming scheme and storage location
        // For example:
        String directory = cordova.getActivity().getExternalFilesDir(null).getAbsolutePath();
        String fileName = "merged_audio_" + System.currentTimeMillis() + ".m4a";
        return directory + "/" + fileName;
    }

    public void mergeFile(String[] audioFiles, String outputFile, boolean parallelMixing, CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < audioFiles.length; i++) {
                    Log.d("AudioMerger", "Original URI: " + audioFiles[i]);
                    try {
                        audioFiles[i] = getPathFromUri(audioFiles[i]);
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                    Log.d("AudioMerger", "Decoded Path: " + audioFiles[i]);
                }
                if (parallelMixing) {
                    // Merge audio files in parallel
                    mergeInParallel(audioFiles, outputFile, callbackContext);
                } else {
                    // Merge audio files sequentially
                    mergeSequentially(audioFiles, outputFile, callbackContext);
                }
            }
        });

    }

    private void mergeInParallel(String[] audioFiles, String outputFile, CallbackContext callbackContext) {
        // Construct the complex filter command for FFmpeg
        StringBuilder filterComplex = new StringBuilder();
        filterComplex.append("-filter_complex \"");

        // Add input streams to the filter
        for (int i = 0; i < audioFiles.length; i++) {
            filterComplex.append("[").append(i).append(":a]");
        }

        // Use the amerge filter to merge the streams
        filterComplex.append("amerge=inputs=").append(audioFiles.length);

        // Map the merged stream to the output
        filterComplex.append("[a]\" -map \"[a]\"");

        // Set the number of audio channels to 2 (stereo); adjust if needed
        filterComplex.append(" -ac 2");

        // Complete the FFmpeg command
        String ffmpegCommand = "";
        for (String audioFile : audioFiles) {
            ffmpegCommand += "-i '" + audioFile + "' ";
        }
        ffmpegCommand += filterComplex.toString() + " " + outputFile;

        Config.enableLogCallback(new LogCallback() {
            public void apply(LogMessage message) {
                Log.d(Config.TAG, message.getText());
            }
        });

        long maxDuration = getMaxDuration(audioFiles);

        Config.enableStatisticsCallback(new StatisticsCallback() {
            public void apply(Statistics newStatistics) {
                float progress = Float.parseFloat(String.valueOf(newStatistics.getTime())) / maxDuration;
                Log.d(Config.TAG, "Progress: "+progress);
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, (int) (progress * 100));
                pluginResult.setKeepCallback(true);
                callbackContext.sendPluginResult(pluginResult);

            }
        });

        // Run the FFmpeg command asynchronously
        FFmpeg.executeAsync(ffmpegCommand, new ExecuteCallback() {
            @Override
            public void apply(final long executionId, final int returnCode) {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (returnCode == RETURN_CODE_SUCCESS) {
                            callbackContext.success(outputFile); // Merging success
                        } else {
                            callbackContext.error("FFmpeg execution failed with return code: " + returnCode); // Merging failed
                        }
                    }
                });
            }
        });
    }


    private void mergeSequentially(String[] audioFiles, String outputFile, CallbackContext callbackContext) {
        try {

            String fileList = createFileList(audioFiles, callbackContext);

            // Build the FFmpeg command using the concat demuxer
            String ffmpegCommand = "-f concat -safe 0 -i " + fileList + " -c copy " + outputFile;


            Config.enableLogCallback(new LogCallback() {
                public void apply(LogMessage message) {
                    Log.d(Config.TAG, message.getText());
                }
            });

            long totalDuration = getTotalDuration(audioFiles);

            Config.enableStatisticsCallback(new StatisticsCallback() {
                public void apply(Statistics newStatistics) {
                    float progress = Float.parseFloat(String.valueOf(newStatistics.getTime())) / totalDuration;
                    Log.d(Config.TAG, "Progress: "+progress);
                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, (int) (progress * 100));
                    pluginResult.setKeepCallback(true);
                    callbackContext.sendPluginResult(pluginResult);

                }
            });
            // Execute the FFmpeg command
            FFmpeg.executeAsync(ffmpegCommand, new ExecuteCallback() {
                @Override
                public void apply(final long executionId, final int returnCode) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (returnCode == RETURN_CODE_SUCCESS) {
                                callbackContext.success(outputFile);
                            } else if (returnCode == RETURN_CODE_CANCEL) {
                                callbackContext.error("Async command execution cancelled by user.");
                            } else {
                                callbackContext.error("Async command execution failed with returnCode=" + returnCode);
                            }
                        }
                    });

                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public long getTotalDuration(String[] audioFiles) {
        long totalDuration = 0L;
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();

            for (String filePath : audioFiles) {
                try {
                    retriever.setDataSource(filePath);
                    String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    totalDuration += Long.parseLong(durationStr);
                } catch (Exception e) {
                    e.printStackTrace();
                    // Handle error - file not found, etc.
                }
            }

            retriever.release();
        } catch (IOException e) {
            Log.d(Config.TAG, "Error calculating the total duration: " + e.getMessage());
        }
         // Release the MediaMetadataRetriever resource
        return totalDuration;
    }

    public String getPathFromUri(String uri) throws UnsupportedEncodingException {
        // Decode the URI in case it's URL encoded
        String decodedUri = URLDecoder.decode(uri, StandardCharsets.UTF_8.name());

        // Remove the 'file://' prefix if it exists
        if (decodedUri.startsWith("file://")) {
            decodedUri = decodedUri.substring(7);
        }

        return decodedUri;
    }

    private String createFileList(String[] audioFiles, CallbackContext callbackContext) {
        BufferedWriter writer = null;
        File fileList = null;
        try {
            // Create a temporary file to hold the file paths
            fileList = File.createTempFile("ffmpeg_file_list", ".txt", cordova.getActivity().getCacheDir());
            writer = new BufferedWriter(new FileWriter(fileList));

            // Write each file path to the text file with the required format for FFmpeg
            for (String audioFile : audioFiles) {
                writer.write("file '" + audioFile.replace("'", "'\\''") + "'");
                writer.newLine();
            }
        } catch (IOException e) {
            // Send an error back to Cordova if file creation or writing fails
            callbackContext.error("Error creating file list: " + e.getMessage());
            return null;
        } finally {
            // Ensure the BufferedWriter is closed properly
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    // Log the error or send an error callback
                    callbackContext.error("Error closing writer: " + e.getMessage());
                }
            }
        }

        // Return the absolute path of the text file
        return fileList.getAbsolutePath();
    }

    public long getMaxDuration(String[] audioFiles) {
        long maxDuration = 0L; // Store the maximum duration
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        

            for (String filePath : audioFiles) {
                try {
                    retriever.setDataSource(filePath);
                    String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    long duration = Long.parseLong(durationStr);
                    if (duration > maxDuration) {
                        maxDuration = duration; // Update max duration if current file's duration is greater
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // Handle error - file not found, cannot read, etc.
                }
            }

            retriever.release();
        } catch(IOException e) {
            Log.d(Config.TAG, "Error calculating the max duration: " + e.getMessage());
        }
        
        return maxDuration; // Return the maximum duration
    }
}

