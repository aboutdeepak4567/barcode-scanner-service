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
    const decodedText = document.getElementById('decoded-text');
    const latencyBadge = document.getElementById('latency-badge');
    const errorMessage = document.getElementById('error-message');
    const copyBtn = document.getElementById('copy-btn');
    const resetBtns = document.querySelectorAll('.reset-btn');
    const scanLine = document.getElementById('scan-line');

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
        if (!file.type.startsWith('image/')) {
            showError("Please upload a valid image file.");
            return;
        }

        // Show preview
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

    function showState(state) {
        // Reset all
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
        decodedText.textContent = text;
        latencyBadge.textContent = `⚡ ${latency}ms`;
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
});
