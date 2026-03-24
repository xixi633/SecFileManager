import SparkMD5 from 'spark-md5';

self.onmessage = (e) => {
    const { file } = e.data;
    const blobSlice = File.prototype.slice || File.prototype.mozSlice || File.prototype.webkitSlice;
    const chunkSize = 2097152; // 2MB
    const chunks = Math.ceil(file.size / chunkSize);
    let currentChunk = 0;
    const spark = new SparkMD5.ArrayBuffer();
    const fileReader = new FileReader();

    fileReader.onload = function (e) {
        spark.append(e.target.result);
        currentChunk++;

        // Report progress
        const progress = Math.round((currentChunk / chunks) * 100);
        self.postMessage({
            type: 'progress',
            progress
        });

        if (currentChunk < chunks) {
            loadNext();
        } else {
            console.log('Finished loading');
            const hash = spark.end();
            self.postMessage({
                type: 'result',
                hash
            });
            self.close();
        }
    };

    fileReader.onerror = function () {
        self.postMessage({
            type: 'error',
            error: "MD5 calculation failed"
        });
        self.close();
    };

    function loadNext() {
        const start = currentChunk * chunkSize;
        const end = ((start + chunkSize) >= file.size) ? file.size : start + chunkSize;
        fileReader.readAsArrayBuffer(blobSlice.call(file, start, end));
    }

    loadNext();
};
