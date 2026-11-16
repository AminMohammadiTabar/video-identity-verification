(() => {
  const uploadForm = document.querySelector('#upload-form');
  const recordButton = document.querySelector('#record');
  if (!recordButton) return;
  const stopButton = document.querySelector('#stop');
  const fileInput = document.querySelector('#video');
  const preview = document.querySelector('#preview');
  const message = document.querySelector('#camera-message');
  let recorder;
  let stream;
  let chunks = [];

  const stopTracks = () => {
    if (stream) stream.getTracks().forEach(track => track.stop());
    stream = null;
  };

  recordButton.addEventListener('click', async () => {
    try {
      if (!navigator.mediaDevices || !window.MediaRecorder) {
        message.textContent = 'مرورگر شما قابلیت ضبط ویدئو را ندارد.';
        return;
      }
      stream = await navigator.mediaDevices.getUserMedia({video: true, audio: true});
      const type = ['video/webm;codecs=vp8,opus', 'video/webm', 'video/mp4']
        .find(t => MediaRecorder.isTypeSupported(t));
      if (!type) throw new Error('فرمت ضبط پشتیبانی نمی‌شود.');
      recorder = new MediaRecorder(stream, {mimeType: type});
      chunks = [];
      recorder.ondataavailable = event => { if (event.data.size) chunks.push(event.data); };
      recorder.onstop = () => {
        const extension = type.startsWith('video/mp4') ? 'mp4' : 'webm';
        const blob = new Blob(chunks, {type: extension === 'mp4' ? 'video/mp4' : 'video/webm'});
        const videoFile = new File([blob], `verification.${extension}`, {type: blob.type});
        const transfer = new DataTransfer();
        transfer.items.add(videoFile);
        fileInput.files = transfer.files;
        preview.src = URL.createObjectURL(blob);
        preview.style.display = 'block';
        message.textContent = 'ضبط انجام شد. می‌توانید پیش‌نمایش را ببینید و ویدئو را ارسال کنید.';
        stopTracks();
        recordButton.disabled = false;
        stopButton.disabled = true;
      };
      recorder.start();
      recordButton.disabled = true;
      stopButton.disabled = false;
      message.textContent = 'در حال ضبط...';
    } catch (error) {
      stopTracks();
      recordButton.disabled = false;
      stopButton.disabled = true;
      message.textContent = 'دسترسی به دوربین یا ضبط ممکن نشد.';
    }
  });
  stopButton.addEventListener('click', () => {
    if (recorder && recorder.state === 'recording') recorder.stop();
  });
  uploadForm.addEventListener('submit', async event => {
    event.preventDefault();
    const button = uploadForm.querySelector('button[type="submit"]');
    const token = uploadForm.querySelector('input[type="hidden"]')?.value;
    if (!fileInput.files.length) return;
    button.disabled = true;
    message.textContent = 'در حال ارسال ویدئو...';
    try {
      // CSRF in a header avoids parsing multipart content before the security filter.
      const response = await fetch('/api/v1/auth/verifications', {
        method: 'POST', body: new FormData(uploadForm),
        headers: {'X-CSRF-TOKEN': token}, credentials: 'same-origin'
      });
      if (response.status === 201) {
        window.location.assign('/verification/upload?submitted');
      } else if (response.status === 409) {
        message.textContent = 'یک درخواست احراز هویت فعال برای شما وجود دارد.';
      } else {
        message.textContent = 'ثبت ویدئو موفق نبود. لطفاً دوباره تلاش کنید.';
      }
    } catch (error) {
      message.textContent = 'ارتباط با سامانه برقرار نشد.';
    } finally {
      button.disabled = false;
    }
  });
  window.addEventListener('pagehide', stopTracks);
})();
