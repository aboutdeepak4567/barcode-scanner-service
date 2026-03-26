document.addEventListener('DOMContentLoaded', () => {
    // Determine the API base URL based on where the UI is hosted
    // When deploying the backend to Render, replace the placeholder below with your actual Render URL!
    const isLocalhost = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
    const API_BASE_URL = isLocalhost 
        ? 'http://localhost:8080' 
        : 'https://barcode-scanner-microservice.onrender.com';

    const dropZone = document.getElementById('drop-zone');
    const fileInput = document.getElementById('file-input');
    const imagePreviewContainer = document.getElementById('image-preview-container');
    const swaggerLink = document.getElementById('swagger-link');

    // Make sure the Swagger link points to the backend (Render), not Vercel
    if (swaggerLink) {
        swaggerLink.href = `${API_BASE_URL}/swagger-ui/index.html`;
    }
    const imagePreview = document.getElementById('image-preview');
    const loadingState = document.getElementById('loading-state');
    const resultContainer = document.getElementById('result-container');
    const errorContainer = document.getElementById('error-container');
    const scannedData = document.getElementById('scanned-data');
    const latencyBadge = document.getElementById('latency-badge');
    const errorMessage = document.getElementById('error-message');
    const copyBtn = document.getElementById('copy-btn');
    const resetBtns = document.querySelectorAll('.reset-btn');
    const scanLine = document.getElementById('scan-line');
    
    // Batch UI Elements
    const singleDataDisplay = document.getElementById('single-data-display');
    const batchDataDisplay = document.getElementById('batch-data-display');
    const batchResultsList = document.getElementById('batch-results-list');
    const batchCountSpan = document.getElementById('batch-count');
    const batchLatencySpan = document.getElementById('batch-latency');

    // Drag and Drop Events
    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, preventDefaults, false);
    });

    function preventDefaults(e) {
        e.preventDefault();
        e.stopPropagation();
    }

    ['dragenter', 'dragover'].forEach(eventName => {
        dropZone.addEventListener(eventName, () => dropZone.classList.add('drag-active'), false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, () => dropZone.classList.remove('drag-active'), false);
    });

    dropZone.addEventListener('drop', (e) => {
        const dt = e.dataTransfer;
        const files = dt.files;
        if (files && files.length > 0) {
            handleFile(files[0]);
        }
    });

    fileInput.addEventListener('change', function() {
        if (this.files && this.files.length > 0) {
            handleFile(this.files[0]);
        }
    });

    function handleFile(file) {
        if (file.name.toLowerCase().endsWith('.zip')) {
            imagePreview.src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="%2300f2fe"><path d="M14 2H6C4.9 2 4 2.9 4 4V20C4 21.1 4.9 22 6 22H18C19.1 22 20 21.1 20 20V8L14 2M13 13V15H11V13H9V11H11V9H13V11H15V13H13Z"/></svg>';
            showState('preview');
            scanBatchZip(file);
            return;
        }

        if (!file.type.startsWith('image/')) {
            showError("Please upload a valid image file or a .zip archive.");
            return;
        }

        const reader = new FileReader();
        reader.readAsDataURL(file);
        reader.onloadend = function() {
            imagePreview.src = reader.result;
            showState('preview');
            scanImage(file);
        }
    }

    async function scanImage(file) {
        const formData = new FormData();
        formData.append('file', file);

        try {
            const response = await fetch(`${API_BASE_URL}/api/v1/scan`, {
                method: 'POST',
                body: formData
            });

            const result = await response.json();

            if (response.ok && result.success) {
                showSuccess(result.data, result.latencyMs);
            } else {
                showError(result.error || "Failed to decode barcode.");
            }
        } catch (error) {
            console.error('Network Error:', error);
            showError("Network error occurred while connecting to the microservice.");
        }
    }

    async function scanBatchZip(file) {
        const formData = new FormData();
        formData.append('file', file);
        try {
            const response = await fetch(`${API_BASE_URL}/api/v1/scan/batch`, { method: 'POST', body: formData });
            const result = await response.json();
            if (response.ok) {
                showBatchSuccess(result);
            } else {
                showError(result.error || "Failed to parse ZIP file.");
            }
        } catch (error) {
            console.error('Network Error:', error);
            showError("Network error occurred connecting to the backend.");
        }
    }

    function showState(state) {
        loadingState.classList.add('hidden');
        resultContainer.classList.add('hidden');
        errorContainer.classList.add('hidden');
        
        if (state === 'preview') {
            dropZone.querySelector('.drop-content').classList.add('hidden');
            imagePreviewContainer.classList.remove('hidden');
            scanLine.classList.remove('hidden');
            loadingState.classList.remove('hidden');
        } else if (state === 'reset') {
            dropZone.querySelector('.drop-content').classList.remove('hidden');
            imagePreviewContainer.classList.add('hidden');
            fileInput.value = '';
        }
    }

    function showSuccess(text, latency) {
        scanLine.classList.add('hidden');
        loadingState.classList.add('hidden');
        resultContainer.classList.remove('hidden');
        
        batchDataDisplay.classList.add('hidden');
        singleDataDisplay.classList.remove('hidden');
        decodedText.textContent = text;
        latencyBadge.textContent = `⚡ ${latency}ms`;
    }

    function showBatchSuccess(data) {
        scanLine.classList.add('hidden');
        loadingState.classList.add('hidden');
        resultContainer.classList.remove('hidden');
        
        singleDataDisplay.classList.add('hidden');
        batchDataDisplay.classList.remove('hidden');
        
        batchCountSpan.textContent = data.totalProcessed;
        batchLatencySpan.textContent = data.totalLatencyMs;
        latencyBadge.textContent = `⚡ ${data.totalLatencyMs}ms Batch`;
        
        batchResultsList.innerHTML = '';
        for (const [filename, extract] of Object.entries(data.results)) {
            const li = document.createElement('li');
            li.innerHTML = `<strong>${filename}</strong> <span>${extract}</span>`;
            batchResultsList.appendChild(li);
        }
    }

    function showError(msg) {
        scanLine.classList.add('hidden');
        loadingState.classList.add('hidden');
        errorContainer.classList.remove('hidden');
        errorMessage.textContent = msg;
    }

    // Reset Buttons
    resetBtns.forEach(btn => {
        btn.addEventListener('click', () => showState('reset'));
    });

    // Copy to clipboard
    copyBtn.addEventListener('click', () => {
        navigator.clipboard.writeText(decodedText.textContent).then(() => {
            const originalText = copyBtn.textContent;
            copyBtn.textContent = 'Copied!';
            setTimeout(() => {
                copyBtn.textContent = originalText;
            }, 2000);
        });
    });

    // Premium 3D Tilt Effect for Interactivity
    const scannerCard = document.querySelector('.scanner-card');
    if (scannerCard) {
        document.addEventListener('mousemove', (e) => {
            // Calculate mouse distance from center to dictate tilt intensity
            const xAxis = (window.innerWidth / 2 - e.pageX) / 45;
            const yAxis = (window.innerHeight / 2 - e.pageY) / 45;
            
            // Apply 3D perspective rotation and dynamic shadow length
            scannerCard.style.transform = `perspective(1000px) rotateY(${xAxis}deg) rotateX(${yAxis}deg)`;
            scannerCard.style.boxShadow = `${-xAxis}px ${yAxis}px 50px rgba(0,0,0,0.5)`;
        });
        
        // Reset position when mouse leaves the document window
        document.addEventListener('mouseleave', () => {
            scannerCard.style.transform = `perspective(1000px) rotateY(0deg) rotateX(0deg)`;
            scannerCard.style.boxShadow = `0 25px 50px -12px rgba(0, 0, 0, 0.5)`;
        });
    }
});
