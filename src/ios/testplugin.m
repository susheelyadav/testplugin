#include <ffmpegkit/FFmpegKitConfig.h>
#include <ffmpegkit/FFmpegKit.h>
#import "AudioMerger.h"

@implementation AudioMerger

- (void)mergeAudioFiles:(CDVInvokedUrlCommand *)command {
    NSArray *fileURIs = [command.arguments objectAtIndex:0]; // Array of file URIs
    NSString *outputFile = [self generateOutputFilePath];
    
    NSString *commandString = [self buildFFmpegCommandWithAudioFiles:fileURIs outputFile:outputFile];
    
    [FFmpegKit executeAsync:commandString withCompleteCallback:^(FFmpegSession *session) {
        SessionState state = [session getState];
        ReturnCode *returnCode = [session getReturnCode];

        if ([ReturnCode isSuccess:returnCode]) {
            NSLog(@"Encode completed successfully.\n");
            CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:outputFile];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        } else {
            NSString *errorLog = [NSString stringWithFormat:@"Encode failed with state %@ and rc %@.%@", [FFmpegKitConfig sessionStateToString:state], returnCode, [session getFailStackTrace] ?: @"\n"];
            NSLog(@"%@", errorLog);
            CDVPluginResult *errorResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errorLog];
            [self.commandDelegate sendPluginResult:errorResult callbackId:command.callbackId];
        }
    }];
}

- (NSString *)buildFFmpegCommandWithAudioFiles:(NSArray<NSString *> *)audioFiles outputFile:(NSString *)outputFile {
    NSString *fileList = [self createFileListWithAudioFiles:audioFiles];

    // Build the FFmpeg command using the concat demuxer
    NSString *ffmpegCommand = [NSString stringWithFormat:@"-f concat -safe 0 -protocol_whitelist file,http,https,tcp,tls,crypto -i %@ -c copy %@", fileList, outputFile];

    return ffmpegCommand;
}

- (NSString *)generateOutputFilePath {
    // Get the Temporary directory
    NSString *tempDirectory = NSTemporaryDirectory();

    // Create a file name based on the current timestamp
    NSTimeInterval timeStamp = [[NSDate date] timeIntervalSince1970];
    NSString *fileName = [NSString stringWithFormat:@"merged_audio_%ld.m4a", (long)timeStamp];

    // Combine the directory and file name
    NSString *filePath = [tempDirectory stringByAppendingPathComponent:fileName];
    return filePath;
}

- (NSString *)createFileListWithAudioFiles:(NSArray<NSString *> *)audioFiles {
    NSFileManager *fileManager = [NSFileManager defaultManager];
    NSString *tempDirectory = NSTemporaryDirectory();
    NSString *tempFilePath = [tempDirectory stringByAppendingPathComponent:@"ffmpeg_file_list.txt"];

    // Create a temporary file
    [fileManager createFileAtPath:tempFilePath contents:nil attributes:nil];
    NSFileHandle *fileHandle = [NSFileHandle fileHandleForWritingAtPath:tempFilePath];

    if (!fileHandle) {
        NSLog(@"Error creating file handle for writing");
        return nil;
    }

    // Write each file path to the text file
    for (NSString *audioFile in audioFiles) {
        NSString *safeAudioFile = [audioFile stringByReplacingOccurrencesOfString:@"'" withString:@"'\\''"];
        NSString *line = [NSString stringWithFormat:@"file '%@'\n", safeAudioFile];
        NSData *lineData = [line dataUsingEncoding:NSUTF8StringEncoding];

        if (lineData) {
            [fileHandle writeData:lineData];
        }
    }

    [fileHandle closeFile];
    return tempFilePath;
}

@end
