package me.clarius.sdk.solum.example;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.HashMap;
import java.util.Map;

public class ProbeStore extends ViewModel {
    public Map<String, Probe> probeMap = new HashMap<>();

    public MutableLiveData<Probe> probeUpdated = new MutableLiveData<>();
}
