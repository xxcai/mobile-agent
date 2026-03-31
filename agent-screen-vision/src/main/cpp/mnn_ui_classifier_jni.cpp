#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <memory>
#include <new>
#include <vector>

#include <MNN/MNNForwardType.h>
#include <MNN/expr/Executor.hpp>
#include <MNN/expr/ExecutorScope.hpp>
#include <MNN/expr/NeuralNetWorkOp.hpp>

#include "mnn_ui_model_weights.h"

namespace {

constexpr const char* kLogTag = "ScreenVisionMnn";

class UiFeatureMnnModel {
public:
    explicit UiFeatureMnnModel(int numThreads) {
        MNN::BackendConfig config;
        config.precision = MNN::BackendConfig::Precision_Normal;
        config.memory = MNN::BackendConfig::Memory_Normal;
        config.power = MNN::BackendConfig::Power_Normal;

        executor_ = MNN::Express::Executor::newExecutor(MNN_FORWARD_CPU, config, std::max(1, numThreads));
        if (executor_ == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Failed to create MNN executor");
            return;
        }

        MNN::Express::ExecutorScope scope(executor_);
        executor_->setGlobalExecutorConfig(MNN_FORWARD_CPU, config, std::max(1, numThreads));

        input_ = MNN::Express::_Input(
                {1, screenvision::sdk::mnnmodel::kInputDim},
                MNN::Express::NHWC,
                halide_type_of<float>()
        );
        if (input_.get() == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Failed to create MNN input variable");
            return;
        }

        probs_ = buildGraph(input_);
        ready_ = probs_.get() != nullptr;
    }

    bool isReady() const {
        return ready_;
    }

    bool predict(const float* featureValues, int featureCount, std::vector<float>* outScores) {
        if (!ready_ || featureValues == nullptr || outScores == nullptr) {
            return false;
        }
        if (featureCount != screenvision::sdk::mnnmodel::kInputDim) {
            __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Unexpected feature count: %d", featureCount);
            return false;
        }

        MNN::Express::ExecutorScope scope(executor_);
        if (!input_->resize({1, featureCount})) {
            __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Failed to resize MNN input");
            return false;
        }

        float* inputPtr = input_->writeMap<float>();
        if (inputPtr == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Failed to map MNN input buffer");
            return false;
        }
        std::copy(featureValues, featureValues + featureCount, inputPtr);
        input_->unMap();

        MNN::Express::Variable::compute({probs_}, false);
        const float* scorePtr = probs_->readMap<float>();
        if (scorePtr == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Failed to map MNN output buffer");
            return false;
        }

        outScores->assign(scorePtr, scorePtr + screenvision::sdk::mnnmodel::kOutputDim);
        probs_->unMap();
        return true;
    }

private:
    static std::vector<float> copyWeights(const float* data, int count) {
        return std::vector<float>(data, data + count);
    }

    static MNN::Express::VARP buildGraph(const MNN::Express::VARP& input) {
        auto fc1 = MNN::Express::_InnerProduct(
                copyWeights(
                        screenvision::sdk::mnnmodel::kFc1Weight,
                        static_cast<int>(sizeof(screenvision::sdk::mnnmodel::kFc1Weight) / sizeof(float))
                ),
                copyWeights(
                        screenvision::sdk::mnnmodel::kFc1Bias,
                        static_cast<int>(sizeof(screenvision::sdk::mnnmodel::kFc1Bias) / sizeof(float))
                ),
                input,
                {1, screenvision::sdk::mnnmodel::kHidden1Dim}
        );
        auto relu1 = MNN::Express::_Relu(fc1);

        auto fc2 = MNN::Express::_InnerProduct(
                copyWeights(
                        screenvision::sdk::mnnmodel::kFc2Weight,
                        static_cast<int>(sizeof(screenvision::sdk::mnnmodel::kFc2Weight) / sizeof(float))
                ),
                copyWeights(
                        screenvision::sdk::mnnmodel::kFc2Bias,
                        static_cast<int>(sizeof(screenvision::sdk::mnnmodel::kFc2Bias) / sizeof(float))
                ),
                relu1,
                {1, screenvision::sdk::mnnmodel::kHidden2Dim}
        );
        auto relu2 = MNN::Express::_Relu(fc2);

        auto logits = MNN::Express::_InnerProduct(
                copyWeights(
                        screenvision::sdk::mnnmodel::kFc3Weight,
                        static_cast<int>(sizeof(screenvision::sdk::mnnmodel::kFc3Weight) / sizeof(float))
                ),
                copyWeights(
                        screenvision::sdk::mnnmodel::kFc3Bias,
                        static_cast<int>(sizeof(screenvision::sdk::mnnmodel::kFc3Bias) / sizeof(float))
                ),
                relu2,
                {1, screenvision::sdk::mnnmodel::kOutputDim}
        );

        return MNN::Express::_Softmax(logits, -1);
    }

    std::shared_ptr<MNN::Express::Executor> executor_;
    MNN::Express::VARP input_;
    MNN::Express::VARP probs_;
    bool ready_ = false;
};

UiFeatureMnnModel* fromHandle(jlong handle) {
    return reinterpret_cast<UiFeatureMnnModel*>(handle);
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_screenvision_sdk_internal_mnn_MnnUiClassifier_nativeCreate(
        JNIEnv*,
        jclass,
        jint numThreads
) {
    auto* model = new (std::nothrow) UiFeatureMnnModel(static_cast<int>(numThreads));
    if (model == nullptr || !model->isReady()) {
        delete model;
        return 0;
    }
    return reinterpret_cast<jlong>(model);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_screenvision_sdk_internal_mnn_MnnUiClassifier_nativePredict(
        JNIEnv* env,
        jclass,
        jlong handle,
        jfloatArray featureVector
) {
    auto* model = fromHandle(handle);
    if (model == nullptr || featureVector == nullptr) {
        return nullptr;
    }

    const jsize featureCount = env->GetArrayLength(featureVector);
    if (featureCount != screenvision::sdk::mnnmodel::kInputDim) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                kLogTag,
                "Invalid feature vector length: %d",
                static_cast<int>(featureCount)
        );
        return nullptr;
    }

    jboolean isCopy = JNI_FALSE;
    jfloat* featurePtr = env->GetFloatArrayElements(featureVector, &isCopy);
    if (featurePtr == nullptr) {
        return nullptr;
    }

    std::vector<float> scores;
    const bool ok = model->predict(featurePtr, static_cast<int>(featureCount), &scores);
    env->ReleaseFloatArrayElements(featureVector, featurePtr, JNI_ABORT);
    if (!ok || scores.size() != screenvision::sdk::mnnmodel::kOutputDim) {
        return nullptr;
    }

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(scores.size()));
    if (result == nullptr) {
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(scores.size()), scores.data());
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_screenvision_sdk_internal_mnn_MnnUiClassifier_nativeRelease(
        JNIEnv*,
        jclass,
        jlong handle
) {
    delete fromHandle(handle);
}