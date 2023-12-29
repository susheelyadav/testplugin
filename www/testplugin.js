
var exec = require("cordova/exec");

var AudioMerger = {
	mergeAudioFiles: function (audioFiles, success, error, parallelMixing) {
		exec(success, error, "testplugin", "mergeAudioFiles", [
			audioFiles,
			parallelMixing,
		]);
	},
};

module.exports = AudioMerger;

