// This is used because download from native side won't have session changes.

function gonativeDownloadBlobUrl(url) {
	var req = new XMLHttpRequest();
	req.open('GET', url, true);
	req.responseType = 'blob';

	req.onload = function(event) {
		var blob = req.response;
		saveBlob(blob);
	};
	req.send();

	function sendMessage(message) {
	    if (window.webkit && window.webkit.messageHandlers &&
	        window.webkit.messageHandlers.fileWriterSharer) {
	        window.webkit.messageHandlers.fileWriterSharer.postMessage(message);
	    }
	    if (window.gonative_file_writer_sharer && window.gonative_file_writer_sharer.postMessage) {
			window.gonative_file_writer_sharer.postMessage(JSON.stringify(message));
	    }
	}

	function saveBlob(blob, filename) {
	    var chunkSize = 1024 * 1024; // 1mb
	    var index = 0;
	    // random string to identify this file transfer
	    var id = Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);

	    function sendHeader() {
	        sendMessage({
	            event: 'fileStart',
	            id: id,
	            size: blob.size,
	            type: blob.type,
	            name: filename
	        });
	    }

	    function sendChunk() {
	        if (index >= blob.size) {
	            return sendEnd();
	        }

	        var chunkToSend = blob.slice(index, index + chunkSize);
	        var reader = new FileReader();
	        reader.readAsDataURL(chunkToSend);
	        reader.onloadend = function() {
	            sendMessage({
	                event: 'fileChunk',
	                id: id,
	                data: reader.result
	            });
	            index += chunkSize;
	            setTimeout(sendChunk);
	        };
	    }
	    
	    function sendEnd() {
	        sendMessage({
	            event: 'fileEnd',
	            id:id
	        });
	    }
	    
	    sendHeader();
	    gonative_run_after_storage_permissions.push(sendChunk);
	}
}

gonative_run_after_storage_permissions = [];
function gonativeGotStoragePermissions() {
    while (gonative_run_after_storage_permissions.length > 0) {
        var run = gonative_run_after_storage_permissions.shift();
        run();
    }
}
