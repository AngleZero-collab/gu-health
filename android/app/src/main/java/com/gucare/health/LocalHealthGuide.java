package com.gucare.health;

/**
 * Deterministic edge-AI simulation, NOT a trained model, diagnosis, or free-form chatbot.
 * No Context, database, logging, network, analytics, or retained user input.
 * Persona: 你是一位專為台灣長輩服務的健康管家。只能提供基本衛教並建議就診科別，
 * 不能進行醫療診斷。保持3句話內。
 */
final class LocalHealthGuide {
    private LocalHealthGuide(){}
    static char[] reply(char[] input){
        if(containsAny(input,"胸痛","胸口痛","喘不過氣","呼吸困難","昏倒","嘴歪","手腳無力","說話不清","想自殺","不想活"))
            return "我不能進行診斷。若您現在胸痛、呼吸困難、突然無力或有立即危險，請立刻撥打一一九，或請身邊的人協助去急診。不要等待語音問答或自行開車。".toCharArray();
        if(containsAny(input,"眼","視力","看不清","黑影","線條","视力","看不见"))
            return "我不能判定眼睛是否有疾病。若視力突然下降、線條變形或出現黑影，請儘速就醫。平時的視覺困擾，可以向眼科詢問。".toCharArray();
        if(containsAny(input,"牙","口腔","牙齦"))
            return "我不能診斷牙齒或口腔疾病。可以保持日常口腔清潔，若疼痛、流血或潰瘍持續，請找牙科檢查。若有嚴重腫脹或呼吸困難，請立即就醫。".toCharArray();
        if(containsAny(input,"藥","药","劑量","停藥","加藥"))
            return "我不能建議您自行調整藥物或劑量。請帶著藥袋詢問原開藥醫師或藥師，不要自行停藥或加藥。若有急性嚴重不適，請立即就醫。".toCharArray();
        if(containsAny(input,"篩檢","健檢","檢查","筛检","健检"))
            return "可以在首頁查看初步符合的篩檢項目。實際資格、時間與費用，請先向院所確認，也可以詢問家庭醫學科。若已有不舒服的症狀，請直接就醫，不要只等待篩檢。".toCharArray();
        if(containsAny(input,"血壓","血糖","頭暈","血压","头晕"))
            return "我不能根據語音內容診斷疾病。可以把量測數值與不舒服的時間記下來，帶到家庭醫學科或內科討論。若突然劇烈不適、胸痛或無力，請立即就醫。".toCharArray();
        return "我是提供基本衛教的健康管家，不能進行醫療診斷。身體持續不舒服或不知道要看哪一科，可以先詢問家庭醫學科。若突然出現嚴重不適，請立即就醫。".toCharArray();
    }
    private static boolean containsAny(char[] input,String...terms){
        if(input==null)return false;
        for(String term:terms)for(int i=0;i<=input.length-term.length();i++){
            int j=0;while(j<term.length()&&input[i+j]==term.charAt(j))j++;if(j==term.length())return true;
        }return false;
    }
}
