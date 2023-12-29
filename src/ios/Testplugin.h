#import <Foundation/Foundation.h>
#import <Cordova/CDV.h>
#import <ffmpegkit/FFmpegKitConfig.h>
#import <ffmpegkit/FFmpegKit.h>

@interface Testplugin : CDVPlugin

- (void)mergeAudioFiles:(CDVInvokedUrlCommand *)command;
- (NSString *)buildFFmpegCommandWithAudioFiles:(NSArray<NSString *> *)audioFiles outputFile:(NSString *)outputFile;
- (NSString *)generateOutputFilePath;
- (NSString *)createFileListWithAudioFiles:(NSArray<NSString *> *)audioFiles;

@end
