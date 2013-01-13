if (phantom.args.length != 1) {
    console.log('Expected a target URL parameter.');
    phantom.exit(1);
}

var page = require('webpage').create();
var url = phantom.args[0];

page.onConsoleMessage = function (message) {
    console.log("Test console: " + message);
};

console.log("Loading URL: " + url);

page.open(url, function (status) {
    if (status != "success") {
        console.log('Failed to open ' + url);
        phantom.exit(1);
    }

    console.log("Running test.");

    setInterval(function() {
    	var result = page.evaluate(function() { return G_testRunner.isFinished() ? G_testRunner.isSuccess() : "_running"; });
    	if (result !== "_running") {
    		if (result) {
    			console.log("Test succeeded.");
    			phantom.exit(0);
    		} else {
        		console.log("*** Test failed! ***");
        		phantom.exit(1);
        	}
    	}
    }, 100);
});
