var exec = require("child_process").exec;

module.exports = function (context) {
	if (context.opts.platforms.includes("ios")) {
		console.log("Installing CocoaPods...");
		exec("cd platforms/ios && pod install", function (error, stdout, stderr) {
			console.log("log => ", stdout);
			console.error("error => ", stderr);
			if (error !== null) {
				console.error(
					'CocoaPods installation failed. Please run "pod install" manually.'
				);
			}
		});
	}
};
