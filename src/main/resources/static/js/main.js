// 全局变量存储上传的文件
let clothesFiles = []; // 穿版衣服文件
let referenceFiles = []; // 参考穿搭文件

// 页面加载完成后初始化
window.onload = function() {
    // 获取DOM元素
    const recommendBtn = document.getElementById('recommendBtn');
    const loading = document.getElementById('loading');
    const resultSection = document.getElementById('resultSection');
    const resultText = document.getElementById('resultText');
    const resultImgList = document.getElementById('resultImgList');
    const errorTip = document.getElementById('errorTip');
    const styleSelect = document.getElementById('style');
    const sceneSelect = document.getElementById('scene');
    const seasonSelect = document.getElementById('season');

    // 1. 穿版衣服图片上传处理
    const clothesFileInput = document.getElementById('clothesFileInput');
    const clothesPreviewList = document.getElementById('clothesPreviewList');
    clothesFileInput.addEventListener('change', function(e) {
        handleFileUpload(e, clothesFiles, clothesPreviewList, 3, 'clothes');
    });

    // 2. 参考穿搭图片上传处理
    const referenceFileInput = document.getElementById('referenceFileInput');
    const referencePreviewList = document.getElementById('referencePreviewList');
    referenceFileInput.addEventListener('change', function(e) {
        handleFileUpload(e, referenceFiles, referencePreviewList, 3, 'reference');
    });

    // 3. 提交按钮点击事件
    recommendBtn.addEventListener('click', function() {
        // 校验是否选择了至少1张图片
        if (clothesFiles.length === 0) {
            alert('请至少上传1张需要穿版的衣服图片！');
            return;
        }

        // 展示加载状态
        loading.classList.remove('hidden');
        resultSection.classList.add('hidden');
        errorTip.classList.add('hidden');
        resultText.innerHTML = '';
        resultImgList.innerHTML = '';

        // 构建FormData（支持文件上传）
        const formData = new FormData();
        // 添加筛选条件
        formData.append('style', styleSelect.value);
        formData.append('scene', sceneSelect.value);
        formData.append('season', seasonSelect.value);
        // 添加穿版衣服图片
        clothesFiles.forEach((file, index) => {
            formData.append('clothesFiles', file, `clothes_${index}.${file.name.split('.').pop()}`);
        });
        // 添加参考穿搭图片
        referenceFiles.forEach((file, index) => {
            formData.append('referenceFiles', file, `reference_${index}.${file.name.split('.').pop()}`);
        });

        // 调用后端接口（与后端`/TestController/recommend`匹配）
        fetch('/TestController/recommend', {
            method: 'POST', // 文件上传必须用POST
            body: formData,
            // 不要设置Content-Type，浏览器会自动添加multipart/form-data边界
        })
            .then(response => {
                if (!response.ok) {
                    throw new Error('接口请求失败');
                }
                return response.json(); // 后端返回JSON格式（文字+图片地址）
            })
            .then(data => {
                // 隐藏加载状态，展示结果
                loading.classList.add('hidden');
                resultSection.classList.remove('hidden');

                // 渲染文字结果
                resultText.innerHTML = data.recommendText || '暂无穿搭推荐描述';

                // 渲染图片结果
                if (data.recommendImgs && data.recommendImgs.length > 0) {
                    data.recommendImgs.forEach(imgUrl => {
                        const imgItem = document.createElement('div');
                        imgItem.className = 'result-img-item';
                        imgItem.innerHTML = `<img src="${imgUrl}" alt="穿搭推荐图片">`;
                        resultImgList.appendChild(imgItem);
                    });
                } else {
                    resultImgList.innerHTML = '<p>暂无推荐图片</p>';
                }
            })
            .catch(error => {
                // 处理错误
                loading.classList.add('hidden');
                errorTip.classList.remove('hidden');
                console.error('请求错误：', error);
            });
    });
};

/**
 * 处理文件上传和预览
 * @param {Event} e - 文件选择事件
 * @param {Array} fileList - 存储文件的数组
 * @param {HTMLElement} previewList - 预览容器
 * @param {Number} maxCount - 最大上传数量
 * @param {String} type - 类型（clothes/reference）
 */
function handleFileUpload(e, fileList, previewList, maxCount, type) {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    // 校验数量
    const remainCount = maxCount - fileList.length;
    if (files.length > remainCount) {
        alert(`最多只能上传${maxCount}张${type === 'clothes' ? '穿版衣服' : '参考穿搭'}图片！`);
        return;
    }

    // 处理文件
    for (let i = 0; i < files.length; i++) {
        const file = files[i];
        // 只允许图片文件
        if (!file.type.startsWith('image/')) {
            alert('请选择图片文件！');
            continue;
        }

        // 添加到文件数组
        fileList.push(file);

        // 创建预览项
        const previewItem = document.createElement('div');
        previewItem.className = 'preview-item';
        previewItem.dataset.index = fileList.length - 1;
        previewItem.dataset.type = type;

        // 生成图片预览URL
        const reader = new FileReader();
        reader.onload = function(event) {
            previewItem.innerHTML = `
                <img src="${event.target.result}" alt="预览图片">
                <button class="delete-btn" onclick="deletePreviewItem('${type}', ${previewItem.dataset.index})">×</button>
            `;
        };
        reader.readAsDataURL(file);

        // 添加到预览容器
        previewList.appendChild(previewItem);
    }

    // 清空input值，允许重复选择同一文件
    e.target.value = '';
}

/**
 * 删除预览项
 * @param {String} type - 类型（clothes/reference）
 * @param {Number} index - 索引
 */
function deletePreviewItem(type, index) {
    // 删除文件数组中的文件
    if (type === 'clothes') {
        clothesFiles.splice(index, 1);
    } else {
        referenceFiles.splice(index, 1);
    }

    // 删除预览DOM，并重新更新索引
    const previewList = document.getElementById(`${type}PreviewList`);
    previewList.innerHTML = '';
    const fileList = type === 'clothes' ? clothesFiles : referenceFiles;

    // 重新渲染预览
    fileList.forEach((file, newIndex) => {
        const previewItem = document.createElement('div');
        previewItem.className = 'preview-item';
        previewItem.dataset.index = newIndex;
        previewItem.dataset.type = type;

        const reader = new FileReader();
        reader.onload = function(event) {
            previewItem.innerHTML = `
                <img src="${event.target.result}" alt="预览图片">
                <button class="delete-btn" onclick="deletePreviewItem('${type}', ${newIndex})">×</button>
            `;
            previewList.appendChild(previewItem);
        };
        reader.readAsDataURL(file);
    });
}