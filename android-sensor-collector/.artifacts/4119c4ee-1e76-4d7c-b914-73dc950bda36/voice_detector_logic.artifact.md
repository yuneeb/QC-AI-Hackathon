# VoiceDetector Logic Flow

This document visualizes how the `VoiceDetector` function calculates the confidence score (0-100%) for responding via voice.

## Decision Flowchart

```mermaid
flowchart TD
    %% Node Definitions
    Start([Input Data Received])
    CallCheck{Is User on a Call?}
    BaseScore[Set Neutral Baseline: 40%]

    subgraph Environment ["Stage 1: Environment & Hands-Free"]
        HandsFreeCalc[Detect Hands-Free:<br/>Bluetooth or Wired Headset]
        ScreenCheck{Is Screen On?}
        ScreenOff[+20% Confidence<br/>'Eyes-Free Mode']
        HandsFreePenalty{Is Hands-Free?}
        ReducedPenalty[-5% Confidence<br/>'Screen is on, but hands are busy']
        FullPenalty[-20% Confidence<br/>'Likely prefers reading text']
        HFBonus[+30% Confidence<br/>'Audio Routing Optimized']
    end

    subgraph UserIntent ["Stage 2: User Intent & Context"]
        DNDCheck{DND Active?}
        DNDPenalty[-40% Confidence<br/>'User wants silence']
        ActivityCheck{Current Activity?}
        Driving[+40% Confidence<br/>'Driving/In Vehicle']
        Moving[+25% Confidence<br/>'Walking/Running']
        Still[-10% Confidence<br/>'Stationary/Still']
    end

    subgraph Resources ["Stage 3: Device Resources"]
        BatteryCheck{Battery Score?}
        Bat1[-30% Confidence<br/>'Critical Battery']
        Bat2[-15% Confidence<br/>'Low Battery']
        Bat3[-5% Confidence<br/>'Fair Battery']
    end

    FinalClamp[Clamp Final Score<br/>0% to 100%]
    End([Final Voice Confidence Result])

    %% Connections
    Start --> CallCheck
    CallCheck -- "Yes (Not Idle)" --> ReturnZero
    ReturnZero([Return 0% Confidence])

    CallCheck -- "No (Idle)" --> BaseScore
    BaseScore --> HandsFreeCalc
    HandsFreeCalc --> ScreenCheck

    ScreenCheck -- "Off" --> ScreenOff
    ScreenCheck -- "On" --> HandsFreePenalty

    HandsFreePenalty -- "Yes" --> ReducedPenalty
    HandsFreePenalty -- "No" --> FullPenalty

    ScreenOff --> HFBonusCheck{Is Hands-Free?}
    ReducedPenalty --> HFBonusCheck
    FullPenalty --> HFBonusCheck

    HFBonusCheck -- "Yes" --> HFBonus
    HFBonusCheck -- "No" --> DNDCheck

    HFBonus --> DNDCheck
    DNDCheck -- "Yes" --> DNDPenalty
    DNDCheck -- "No" --> ActivityCheck

    DNDPenalty --> ActivityCheck
    ActivityCheck -- "Driving" --> Driving
    ActivityCheck -- "Active" --> Moving
    ActivityCheck -- "Still" --> Still
    ActivityCheck -- "None" --> BatteryCheck

    Driving --> BatteryCheck
    Moving --> BatteryCheck
    Still --> BatteryCheck

    BatteryCheck -- "1" --> Bat1
    BatteryCheck -- "2" --> Bat2
    BatteryCheck -- "3" --> Bat3
    BatteryCheck -- "4/5" --> FinalClamp

    Bat1 --> FinalClamp
    Bat2 --> FinalClamp
    Bat3 --> FinalClamp

    FinalClamp --> End

    %% Styling
    style ReturnZero fill:#f96,stroke:#333,stroke-width:2px
    style HFBonus fill:#9f9,stroke:#333,stroke-width:2px
    style DNDPenalty fill:#f99,stroke:#333,stroke-width:2px
    style Driving fill:#9f9,stroke:#333,stroke-width:2px
    style Bat1 fill:#f99,stroke:#333,stroke-width:2px
```

## Quick Reference Table

| Category | Signal | Impact | Logic |
| :--- | :--- | :--- | :--- |
| **Safety** | Call State | 🛑 **Total Block** | If not "IDLE", confidence = 0%. |
| **Environment** | Hands-Free | 🚀 **+30%** | Bluetooth/Headsets are optimized for voice. |
| **Visibility** | Screen State | 👁️ **+20% / -20%** | Screen off favors voice; Screen on favors text. |
| **Hands-Free Logic**| Screen On + HF | ⚖️ **-5%** | Penalty is mitigated if user is hands-free. |
| **Silence** | DND Active | 🤫 **-40%** | Explicit request for no interruptions. |
| **Power** | Battery Score | 🔋 **Up to -30%** | Voice tasks are heavy; lower confidence saves power. |
