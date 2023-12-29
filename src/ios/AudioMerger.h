#import <Foundation/Foundation.h>
#import <Cordova/CDV.h>

@interface AudioMerger : CDVPlugin {}

- (void)mergeAudioFiles:(CDVInvokedUrlCommand *)command;

@end