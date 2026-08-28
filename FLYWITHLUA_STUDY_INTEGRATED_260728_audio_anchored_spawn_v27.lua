-- FlyWithLua integrated study script
-- Combines:
-- 1. Scenario command control
-- 2. Cloud-cover situation injection
-- 3. Ownship STATE UDP export
-- 4. Intruder INTRUDER UDP export
-- 5. Manual EVENT UDP export
-- 6. Head-on intruder scenario using multiplayer slot 1
-- 7. Randomized final-approach crossing intruder scenario

local socket = require("socket")
local udp = socket.udp()
udp:settimeout(0)
math.randomseed(os.time())

local TARGET_HOST = "127.0.0.1"
local TARGET_PORT = 9100
local STUDY_SCRIPT_VERSION = "260728_audio_anchored_spawn_v27"
local PARTICIPANT_NOTICE_DURATION_S = 2.5
local TASK_AUDIO_PROTOCOL = "audio_task_v27"
local TASK_AUDIO_BASELINE_DELAY_S = 3.0
local TASK_AUDIO_DURATION_S = 6.493
local TASK_AUDIO_FILE_NAME = "apisat_task_maintain_70kias_centerline_en_us.wav"
local TASK_AUDIO_WAV_PATH = SCRIPT_DIRECTORY .. "AISimulationProjectAssets/" .. TASK_AUDIO_FILE_NAME
local CLOUD_LAYER_BASE_MSL_M = 304.8
local CLOUD_LAYER_TOP_MSL_M = 3657.6
local CLOUD_LAYER_COVERAGE = 5
local METERS_PER_DEG_LAT = 111320.0
local VISUAL_ADVISORY_HAZARD_HORIZONTAL_M = 150.0
local VISUAL_ADVISORY_HAZARD_VERTICAL_M = 30.0
local VISUAL_ADVISORY_CLEAR_HORIZONTAL_M = 200.0
local VISUAL_ADVISORY_CLEAR_VERTICAL_M = 50.0

-- Ownship state datarefs
dataref("local_x", "sim/flightmodel/position/local_x")
dataref("local_y", "sim/flightmodel/position/local_y")
dataref("local_z", "sim/flightmodel/position/local_z")
dataref("latitude_deg", "sim/flightmodel/position/latitude")
dataref("longitude_deg", "sim/flightmodel/position/longitude")
dataref("elevation_m", "sim/flightmodel/position/elevation")
dataref("y_agl_m", "sim/flightmodel/position/y_agl")

dataref("local_vx", "sim/flightmodel/position/local_vx")
dataref("local_vy", "sim/flightmodel/position/local_vy")
dataref("local_vz", "sim/flightmodel/position/local_vz")

dataref("psi_deg", "sim/flightmodel/position/psi")
dataref("theta_deg", "sim/flightmodel/position/theta")
dataref("phi_deg", "sim/flightmodel/position/phi")

dataref("p_rate", "sim/flightmodel/position/P")
dataref("q_rate", "sim/flightmodel/position/Q")
dataref("r_rate", "sim/flightmodel/position/R")

dataref("ias_mps", "sim/flightmodel/position/indicated_airspeed")
dataref("tas_mps", "sim/flightmodel/position/true_airspeed")
dataref("vertical_speed_mps", "sim/flightmodel/position/vh_ind")
dataref("running_time_sec", "sim/time/total_running_time_sec")
dataref("sound_master_volume_ratio", "sim/operation/sound/master_volume_ratio")
dataref("sound_interior_volume_ratio", "sim/operation/sound/interior_volume_ratio")
dataref("sound_engine_volume_ratio", "sim/operation/sound/engine_volume_ratio")
dataref("sound_prop_volume_ratio", "sim/operation/sound/prop_volume_ratio")
dataref("sound_enviro_volume_ratio", "sim/operation/sound/enviro_volume_ratio")
dataref("sound_radio_volume_ratio", "sim/operation/sound/radio_volume_ratio")

-- Pilot control input datarefs
dataref("yoke_pitch_ratio", "sim/cockpit2/controls/yoke_pitch_ratio")
dataref("yoke_roll_ratio", "sim/cockpit2/controls/yoke_roll_ratio")
dataref("yoke_heading_ratio", "sim/cockpit2/controls/yoke_heading_ratio")
dataref("throttle_ratio_all", "sim/cockpit2/engine/actuators/throttle_ratio_all")

-- First multiplayer / AI aircraft slot
dataref("plane1_x", "sim/multiplayer/position/plane1_x")
dataref("plane1_y", "sim/multiplayer/position/plane1_y")
dataref("plane1_z", "sim/multiplayer/position/plane1_z")
dataref("plane1_vx", "sim/multiplayer/position/plane1_v_x")
dataref("plane1_vy", "sim/multiplayer/position/plane1_v_y")
dataref("plane1_vz", "sim/multiplayer/position/plane1_v_z")

study_status_text = "Study integrated script loaded: " .. STUDY_SCRIPT_VERSION
study_scenario_active = false
study_event_counter = 0
study_last_event_name = "NONE"
study_last_event_time_s = 0.0
trial_id = 0
trial_active = false
trial_start_time_s = 0.0
visual_advisory_active = false
operator_overlay_visible = false
participant_trial_state_text = "TRIAL IDLE"
participant_trial_notice_text = ""
participant_trial_notice_until_s = -1.0
task_audio_armed = false
task_audio_active = false
task_audio_trigger_time_s = 0.0
task_audio_start_sim_time_s = 0.0
task_audio_start_wall_time_s = 0.0
task_audio_end_wall_time_s = 0.0

local task_audio_sound = nil
local task_audio_file = io.open(TASK_AUDIO_WAV_PATH, "rb")
if task_audio_file then
    task_audio_file:close()
    task_audio_sound = load_WAV_file(TASK_AUDIO_WAV_PATH)
else
    logMsg("[StudyIntegrated] task audio missing: " .. TASK_AUDIO_WAV_PATH)
end

intruder_headon_active = false
intruder_crossing_active = false
random_crossing_armed = false
intruder_start_time_s = 0.0
intruder_start_x = 0.0
intruder_start_y = 0.0
intruder_start_z = 0.0
intruder_forward_x = 0.0
intruder_forward_z = -1.0
intruder_right_x = 1.0
intruder_right_z = 0.0
intruder_side_sign = 1.0
intruder_heading_deg = 180.0
intruder_speed_mps = 120.0
intruder_start_distance_m = 1500.0
intruder_max_duration_s = 25.0
crossing_trigger_time_s = 0.0
crossing_delay_min_s = 3.0
crossing_delay_max_s = 10.0
crossing_planned_audio_to_spawn_s = 0.0
crossing_time_to_conflict_min_s = 10.0
crossing_time_to_conflict_max_s = 13.0
crossing_min_target_ahead_m = 320.0
crossing_max_target_ahead_m = 560.0
crossing_speed_mps = 58.0
crossing_effective_speed_mps = 58.0
crossing_spawn_extra_distance_m = 350.0
crossing_path_lead_time_s = 0.15
crossing_max_duration_s = 34.0
crossing_min_distance_reported = false
crossing_time_to_conflict_s = 10.0
crossing_min_horizontal_distance_m = 999999.0
crossing_start_distance_m = 700.0
crossing_approach_mode = "left"
crossing_target_x = 0.0
crossing_target_z = 0.0
crossing_move_x = 0.0
crossing_move_z = 0.0
crossing_vertical_speed_mps = 0.0
crossing_vertical_match_gain = 0.7
crossing_vertical_speed_limit_mps = 3.0
crossing_stabilizing = false
crossing_stabilization_start_time_s = 0.0
crossing_stabilization_duration_s = 0.35
crossing_spawn_event_recorded = false
crossing_spawn_event_detail = ""

frame_count = 0
sample_every_n_frames = 6
sample_index = 0
last_state_line = ""
last_intruder_line = ""
last_horizontal_distance = 0.0
last_vertical_separation = 0.0
last_sent_sim_time_s = -1.0

logMsg("[StudyIntegrated] script loaded: " .. STUDY_SCRIPT_VERSION)

function study_compute_horizontal_distance()
    local dx = local_x - plane1_x
    local dz = local_z - plane1_z
    return math.sqrt(dx * dx + dz * dz)
end

function study_compute_vertical_separation()
    return math.abs(local_y - plane1_y)
end

function study_compute_intruder_geo()
    local east_m = plane1_x - local_x
    local north_m = -(plane1_z - local_z)
    local lat_rad = math.rad(latitude_deg)
    local meters_per_deg_lon = METERS_PER_DEG_LAT * math.cos(lat_rad)

    if math.abs(meters_per_deg_lon) < 1.0 then
        meters_per_deg_lon = 1.0
    end

    local intruder_latitude_deg = latitude_deg + north_m / METERS_PER_DEG_LAT
    local intruder_longitude_deg = longitude_deg + east_m / meters_per_deg_lon
    local intruder_elevation_m = elevation_m + (plane1_y - local_y)
    return intruder_latitude_deg, intruder_longitude_deg, intruder_elevation_m
end

function study_update_visual_advisory_state()
    if not trial_active then
        visual_advisory_active = false
        return
    end

    if not intruder_headon_active and not intruder_crossing_active and not crossing_stabilizing then
        visual_advisory_active = false
        return
    end

    local current_horizontal_distance = study_compute_horizontal_distance()
    local current_vertical_separation = study_compute_vertical_separation()

    if not visual_advisory_active then
        if current_horizontal_distance <= VISUAL_ADVISORY_HAZARD_HORIZONTAL_M
            and current_vertical_separation <= VISUAL_ADVISORY_HAZARD_VERTICAL_M then
            visual_advisory_active = true
        end
        return
    end

    if current_horizontal_distance >= VISUAL_ADVISORY_CLEAR_HORIZONTAL_M
        or current_vertical_separation >= VISUAL_ADVISORY_CLEAR_VERTICAL_M then
        visual_advisory_active = false
    end
end

function study_heading_from_vector(move_x, move_z)
    local angle_rad = 0.0
    local forward_z_component = -move_z

    if math.abs(forward_z_component) < 0.000001 then
        if move_x >= 0.0 then
            angle_rad = math.pi / 2.0
        else
            angle_rad = -math.pi / 2.0
        end
    else
        angle_rad = math.atan(move_x / forward_z_component)
        if forward_z_component < 0.0 then
            angle_rad = angle_rad + math.pi
        elseif move_x < 0.0 then
            angle_rad = angle_rad + 2.0 * math.pi
        end
    end

    return math.deg(angle_rad) % 360.0
end

function study_clamp(value, min_value, max_value)
    if value < min_value then
        return min_value
    end
    if value > max_value then
        return max_value
    end
    return value
end

function study_build_state_line()
    return string.format(
        "STATE,%d,%d,%.3f,%.3f,%.3f,%.3f,%.8f,%.8f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f",
        trial_id,
        sample_index,
        running_time_sec,
        local_x, local_y, local_z,
        latitude_deg, longitude_deg, elevation_m, y_agl_m,
        local_vx, local_vy, local_vz,
        psi_deg, theta_deg, phi_deg,
        p_rate, q_rate, r_rate,
        ias_mps, tas_mps, vertical_speed_mps,
        yoke_pitch_ratio, yoke_roll_ratio, yoke_heading_ratio,
        throttle_ratio_all
    )
end

function study_build_intruder_line()
    last_horizontal_distance = study_compute_horizontal_distance()
    last_vertical_separation = study_compute_vertical_separation()
    local intruder_latitude_deg, intruder_longitude_deg, intruder_elevation_m = study_compute_intruder_geo()

    return string.format(
        "INTRUDER,%d,%d,%.3f,%.3f,%.3f,%.3f,%.8f,%.8f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f",
        trial_id,
        sample_index,
        running_time_sec,
        plane1_x, plane1_y, plane1_z,
        intruder_latitude_deg, intruder_longitude_deg, intruder_elevation_m,
        plane1_vx, plane1_vy, plane1_vz,
        last_horizontal_distance,
        last_vertical_separation,
        local_x,
        local_y,
        local_z
    )
end

function study_send_udp(line)
    udp:sendto(line, TARGET_HOST, TARGET_PORT)
end

function study_record_event(event_name, detail)
    study_event_counter = study_event_counter + 1
    study_last_event_name = event_name
    study_last_event_time_s = running_time_sec

    local event_line = ""
    if detail == nil or detail == "" then
        event_line = string.format(
            "EVENT,%d,%d,%.3f,%s",
            trial_id,
            study_event_counter,
            study_last_event_time_s,
            event_name
        )
    else
        event_line = string.format(
            "EVENT,%d,%d,%.3f,%s,%s",
            trial_id,
            study_event_counter,
            study_last_event_time_s,
            event_name,
            detail
        )
    end

    study_send_udp(event_line)
    logMsg("[StudyIntegrated] " .. event_line)
end

function study_trial_detail(extra)
    local base = string.format("trial_id=%d", trial_id)
    if extra == nil or extra == "" then
        return base
    end
    return base .. ";" .. extra
end

function study_arm_task_audio()
    task_audio_armed = true
    task_audio_active = false
    task_audio_trigger_time_s = running_time_sec + TASK_AUDIO_BASELINE_DELAY_S
    task_audio_start_sim_time_s = 0.0
    task_audio_start_wall_time_s = 0.0
    task_audio_end_wall_time_s = 0.0
end

function study_start_task_audio()
    if not trial_active or not task_audio_armed then
        return
    end

    task_audio_armed = false
    task_audio_start_sim_time_s = running_time_sec
    task_audio_start_wall_time_s = socket.gettime()
    task_audio_end_wall_time_s = task_audio_start_wall_time_s + TASK_AUDIO_DURATION_S
    study_record_event(
        "TASK_COMMAND_AUDIO_START",
        study_trial_detail(
            string.format(
                "protocol=%s;file=%s;scheduled_duration_s=%.3f",
                TASK_AUDIO_PROTOCOL,
                TASK_AUDIO_FILE_NAME,
                TASK_AUDIO_DURATION_S
            )
        )
    )

    local ok, err = pcall(function()
        play_sound(task_audio_sound)
    end)
    if not ok then
        task_audio_active = false
        participant_trial_notice_text = "TASK AUDIO ERROR"
        participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
        study_record_event(
            "MANUAL_NOTE",
            study_trial_detail("error=task_audio_playback_failed;message=" .. tostring(err))
        )
        logMsg("[StudyIntegrated] task audio playback failed: " .. tostring(err))
        return
    end

    task_audio_active = true
    study_arm_random_crossing(task_audio_start_sim_time_s)
    logMsg("[StudyIntegrated] task audio started: " .. TASK_AUDIO_FILE_NAME)
end

function study_update_task_audio()
    if task_audio_armed and trial_active and running_time_sec >= task_audio_trigger_time_s then
        study_start_task_audio()
    end

    if not task_audio_active or socket.gettime() < task_audio_end_wall_time_s then
        return
    end

    local actual_duration_s = socket.gettime() - task_audio_start_wall_time_s
    task_audio_active = false
    study_record_event(
        "TASK_COMMAND_AUDIO_END",
        study_trial_detail(
            string.format(
                "protocol=%s;file=%s;scheduled_duration_s=%.3f;actual_wall_duration_s=%.3f",
                TASK_AUDIO_PROTOCOL,
                TASK_AUDIO_FILE_NAME,
                TASK_AUDIO_DURATION_S,
                actual_duration_s
            )
        )
    )
    logMsg("[StudyIntegrated] task audio ended: " .. TASK_AUDIO_FILE_NAME)
end

function study_test_task_audio()
    if trial_active then
        logMsg("[StudyIntegrated] ignored task audio test during active trial")
        return
    end
    if task_audio_sound == nil then
        participant_trial_notice_text = "TASK AUDIO NOT READY"
        participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
        logMsg("[StudyIntegrated] task audio test failed; asset not loaded")
        return
    end

    local ok, err = pcall(function()
        play_sound(task_audio_sound)
    end)
    if not ok then
        participant_trial_notice_text = "TASK AUDIO ERROR"
        participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
        logMsg("[StudyIntegrated] task audio test failed: " .. tostring(err))
        return
    end

    participant_trial_notice_text = "TASK AUDIO TEST"
    participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
    logMsg("[StudyIntegrated] task audio test started")
end

function study_reset_trial()
    trial_id = trial_id + 1
    trial_active = false
    trial_start_time_s = 0.0
    task_audio_armed = false
    task_audio_active = false
    task_audio_trigger_time_s = 0.0
    task_audio_start_sim_time_s = 0.0
    task_audio_start_wall_time_s = 0.0
    task_audio_end_wall_time_s = 0.0
    study_scenario_active = false
    intruder_headon_active = false
    intruder_crossing_active = false
    random_crossing_armed = false
    crossing_trigger_time_s = 0.0
    crossing_planned_audio_to_spawn_s = 0.0
    crossing_min_distance_reported = false
    crossing_time_to_conflict_s = 10.0
    crossing_min_horizontal_distance_m = 999999.0
    crossing_start_distance_m = 700.0
    crossing_approach_mode = "left"
    crossing_target_x = 0.0
    crossing_target_z = 0.0
    crossing_move_x = 0.0
    crossing_move_z = 0.0
    crossing_vertical_speed_mps = 0.0
    crossing_effective_speed_mps = crossing_speed_mps
    crossing_stabilizing = false
    crossing_stabilization_start_time_s = 0.0
    crossing_spawn_event_recorded = false
    crossing_spawn_event_detail = ""
    visual_advisory_active = false
    frame_count = 0
    sample_index = 0
    study_event_counter = 0
    study_last_event_name = "NONE"
    study_last_event_time_s = 0.0
    last_state_line = ""
    last_intruder_line = ""
    last_horizontal_distance = 0.0
    last_vertical_separation = 0.0
    last_sent_sim_time_s = -1.0
    participant_trial_notice_text = ""
    participant_trial_notice_until_s = -1.0
    participant_trial_state_text = string.format("TRIAL RESET: %d", trial_id)
    study_status_text = string.format("TRIAL RESET: %d", trial_id)
    study_record_event("TRIAL_RESET", study_trial_detail())
    logMsg("[StudyIntegrated] trial reset: " .. tostring(trial_id))
end

function study_start_trial()
    if trial_active then
        study_status_text = "TRIAL ALREADY ACTIVE"
        study_record_event("MANUAL_NOTE", study_trial_detail("ignored=duplicate_trial_start"))
        return
    end

    if task_audio_sound == nil then
        participant_trial_notice_text = "TASK AUDIO NOT READY"
        participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
        study_status_text = "TASK AUDIO NOT READY"
        study_record_event("MANUAL_NOTE", study_trial_detail("ignored=start_without_task_audio"))
        return
    end

    if trial_id <= 0 then
        study_reset_trial()
    end

    trial_active = true
    trial_start_time_s = running_time_sec
    operator_overlay_visible = false
    participant_trial_notice_text = ""
    participant_trial_notice_until_s = -1.0
    participant_trial_state_text = string.format("TRIAL ACTIVE: %d", trial_id)
    study_status_text = string.format("TRIAL START: %d", trial_id)
    study_record_event(
        "TRIAL_START",
        study_trial_detail(
            string.format(
                "protocol=%s;task_audio_baseline_s=%.3f;sound_master=%.3f;sound_interior=%.3f;sound_engine=%.3f;sound_prop=%.3f;sound_enviro=%.3f;sound_radio=%.3f",
                TASK_AUDIO_PROTOCOL,
                TASK_AUDIO_BASELINE_DELAY_S,
                sound_master_volume_ratio,
                sound_interior_volume_ratio,
                sound_engine_volume_ratio,
                sound_prop_volume_ratio,
                sound_enviro_volume_ratio,
                sound_radio_volume_ratio
            )
        )
    )
    study_arm_task_audio()
end

function study_trial_can_end()
    return not task_audio_armed
        and not task_audio_active
        and not random_crossing_armed
        and not crossing_stabilizing
        and not visual_advisory_active
        and (not intruder_headon_active or last_horizontal_distance >= VISUAL_ADVISORY_CLEAR_HORIZONTAL_M)
        and (not intruder_crossing_active or crossing_min_distance_reported)
end

function study_end_trial_guard_detail()
    return study_trial_detail(
        "ignored=end_before_trial_closed"
        .. ";task_audio_armed=" .. tostring(task_audio_armed)
        .. ";task_audio_active=" .. tostring(task_audio_active)
        .. ";headon=" .. tostring(intruder_headon_active)
        .. ";crossing=" .. tostring(intruder_crossing_active)
        .. ";armed=" .. tostring(random_crossing_armed)
        .. ";stabilizing=" .. tostring(crossing_stabilizing)
        .. ";visual_advisory=" .. tostring(visual_advisory_active)
    )
end

function study_end_trial()
    if not trial_active then
        participant_trial_notice_text = "NO ACTIVE TRIAL"
        participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
        study_status_text = "NO ACTIVE TRIAL"
        study_record_event("MANUAL_NOTE", study_trial_detail("ignored=end_without_active_trial"))
        return
    end

    if not study_trial_can_end() then
        participant_trial_notice_text = "TRIAL IN PROGRESS - END NOT READY"
        participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
        study_status_text = "WAIT: TRIAL NOT CLOSED"
        study_record_event("MANUAL_NOTE", study_end_trial_guard_detail())
        return
    end

    intruder_headon_active = false
    intruder_crossing_active = false
    task_audio_armed = false
    task_audio_active = false
    task_audio_trigger_time_s = 0.0
    task_audio_start_sim_time_s = 0.0
    task_audio_start_wall_time_s = 0.0
    task_audio_end_wall_time_s = 0.0
    random_crossing_armed = false
    crossing_planned_audio_to_spawn_s = 0.0
    crossing_stabilizing = false
    study_scenario_active = false
    visual_advisory_active = false
    trial_active = false
    participant_trial_notice_text = ""
    participant_trial_notice_until_s = -1.0
    participant_trial_state_text = string.format("TRIAL END: %d", trial_id)
    study_status_text = string.format("TRIAL END: %d", trial_id)
    study_record_event("TRIAL_END", study_trial_detail())
end

function study_try_set_dataref(path, value)
    local ok, err = pcall(function()
        set(path, value)
    end)

    if not ok then
        logMsg("[StudyIntegrated] failed to set " .. path .. ": " .. tostring(err))
        return false
    end

    return true
end

function study_try_set_cloud_condition(enabled)
    local ok = true

    if enabled then
        ok = study_try_set_dataref("sim/weather/cloud_base_msl_m[0]", CLOUD_LAYER_BASE_MSL_M) and ok
        ok = study_try_set_dataref("sim/weather/cloud_tops_msl_m[0]", CLOUD_LAYER_TOP_MSL_M) and ok
        ok = study_try_set_dataref("sim/weather/cloud_coverage[0]", CLOUD_LAYER_COVERAGE) and ok
    else
        ok = study_try_set_dataref("sim/weather/cloud_coverage[0]", 0) and ok
    end

    if ok then
        if enabled then
            logMsg("[StudyIntegrated] cloud deck enabled: base=1000ft MSL, top=12000ft MSL")
        else
            logMsg("[StudyIntegrated] cloud deck disabled")
        end
    end

    return ok
end

function study_set_intruder_state(x, y, z, vx, vy, vz)
    local ok = true
    ok = study_try_set_dataref("sim/multiplayer/position/plane1_x", x) and ok
    ok = study_try_set_dataref("sim/multiplayer/position/plane1_y", y) and ok
    ok = study_try_set_dataref("sim/multiplayer/position/plane1_z", z) and ok
    ok = study_try_set_dataref("sim/multiplayer/position/plane1_v_x", vx) and ok
    ok = study_try_set_dataref("sim/multiplayer/position/plane1_v_y", vy) and ok
    ok = study_try_set_dataref("sim/multiplayer/position/plane1_v_z", vz) and ok

    -- Best-effort visual orientation. Keep this fixed during an intruder run.
    study_try_set_dataref("sim/multiplayer/position/plane1_psi", intruder_heading_deg)
    study_try_set_dataref("sim/multiplayer/position/plane1_the", 0.0)
    study_try_set_dataref("sim/multiplayer/position/plane1_phi", 0.0)

    return ok
end

function study_start_intruder_headon()
    if not trial_active then
        study_status_text = "START TRIAL FIRST"
        study_record_event("MANUAL_NOTE", study_trial_detail("ignored=headon_without_active_trial"))
        return
    end

    if intruder_headon_active then
        study_status_text = "HEAD-ON INTRUDER ALREADY ACTIVE"
        study_record_event("MANUAL_NOTE", study_trial_detail("ignored=duplicate_headon_start"))
        return
    end

    intruder_headon_active = true
    intruder_crossing_active = false
    random_crossing_armed = false
    intruder_start_time_s = running_time_sec

    local own_heading_rad = math.rad(psi_deg)
    intruder_forward_x = math.sin(own_heading_rad)
    intruder_forward_z = -math.cos(own_heading_rad)
    intruder_heading_deg = (psi_deg + 180.0) % 360.0

    intruder_start_x = local_x + intruder_forward_x * intruder_start_distance_m
    intruder_start_y = local_y
    intruder_start_z = local_z + intruder_forward_z * intruder_start_distance_m

    local vx = -intruder_forward_x * intruder_speed_mps
    local vy = 0.0
    local vz = -intruder_forward_z * intruder_speed_mps

    local ok = study_set_intruder_state(
        intruder_start_x,
        intruder_start_y,
        intruder_start_z,
        vx,
        vy,
        vz
    )

    if ok then
        study_status_text = "HEAD-ON INTRUDER ACTIVE"
    else
        study_status_text = "HEAD-ON INTRUDER SET FAILED"
    end

    study_record_event("SCENARIO_SELECTED", study_trial_detail("scenario=headon_intruder"))
    study_record_event("INTRUDER_SPAWNED", study_trial_detail("scenario=headon_intruder"))
    logMsg("[StudyIntegrated] head-on intruder started")
end

function study_arm_random_crossing(audio_start_sim_time_s)
    if not trial_active then
        study_status_text = "START TRIAL FIRST"
        study_record_event("MANUAL_NOTE", study_trial_detail("ignored=random_crossing_without_active_trial"))
        return
    end
    if audio_start_sim_time_s == nil or audio_start_sim_time_s <= 0.0 then
        study_status_text = "TASK AUDIO START REQUIRED"
        study_record_event("MANUAL_NOTE", study_trial_detail("ignored=random_crossing_without_audio_start"))
        return
    end

    local delay_range = crossing_delay_max_s - crossing_delay_min_s
    crossing_planned_audio_to_spawn_s = crossing_delay_min_s + math.random() * delay_range
    crossing_trigger_time_s = audio_start_sim_time_s + crossing_planned_audio_to_spawn_s
    random_crossing_armed = true
    intruder_crossing_active = false
    crossing_min_distance_reported = false

    study_status_text = string.format("RANDOM CROSSING ARMED: %.1fs", crossing_planned_audio_to_spawn_s)
    study_record_event(
        "SCENARIO_SELECTED",
        study_trial_detail(
            string.format(
                "scenario=random_final_approach_crossing;delay_anchor=task_audio_start;planned_audio_to_spawn_s=%.3f;audio_start_sim_time_s=%.3f;crossing_trigger_time_s=%.3f",
                crossing_planned_audio_to_spawn_s,
                audio_start_sim_time_s,
                crossing_trigger_time_s
            )
        )
    )
end

function study_start_intruder_crossing()
    if not trial_active then
        study_status_text = "START TRIAL FIRST"
        study_record_event("MANUAL_NOTE", study_trial_detail("ignored=crossing_without_active_trial"))
        return
    end

    intruder_headon_active = false
    intruder_crossing_active = true
    random_crossing_armed = false
    crossing_min_distance_reported = false
    crossing_min_horizontal_distance_m = 999999.0
    crossing_stabilizing = true
    crossing_stabilization_start_time_s = running_time_sec
    crossing_spawn_event_recorded = false
    crossing_spawn_event_detail = ""
    intruder_start_time_s = running_time_sec + crossing_stabilization_duration_s

    local own_heading_rad = math.rad(psi_deg)
    intruder_forward_x = math.sin(own_heading_rad)
    intruder_forward_z = -math.cos(own_heading_rad)
    intruder_right_x = math.cos(own_heading_rad)
    intruder_right_z = math.sin(own_heading_rad)

    local approach_choice = math.random(1, 3)
    if approach_choice == 1 then
        intruder_side_sign = -1.0
        crossing_approach_mode = "left"
    elseif approach_choice == 2 then
        intruder_side_sign = 1.0
        crossing_approach_mode = "right"
    else
        intruder_side_sign = 0.0
        crossing_approach_mode = "front"
    end

    local time_to_conflict_range = crossing_time_to_conflict_max_s - crossing_time_to_conflict_min_s
    crossing_time_to_conflict_s = crossing_time_to_conflict_min_s + math.random() * time_to_conflict_range
    local target_prediction_time_s = crossing_time_to_conflict_s
    local predicted_dx = local_vx * target_prediction_time_s
    local predicted_dz = local_vz * target_prediction_time_s
    local predicted_distance = math.sqrt(predicted_dx * predicted_dx + predicted_dz * predicted_dz)

    if predicted_distance < crossing_min_target_ahead_m then
        predicted_dx = intruder_forward_x * crossing_min_target_ahead_m
        predicted_dz = intruder_forward_z * crossing_min_target_ahead_m
        predicted_distance = crossing_min_target_ahead_m
    elseif predicted_distance > crossing_max_target_ahead_m then
        predicted_dx = predicted_dx / predicted_distance * crossing_max_target_ahead_m
        predicted_dz = predicted_dz / predicted_distance * crossing_max_target_ahead_m
        predicted_distance = crossing_max_target_ahead_m
    end

    local own_speed_horizontal = math.sqrt(local_vx * local_vx + local_vz * local_vz)
    if own_speed_horizontal > 5.0 then
        target_prediction_time_s = predicted_distance / own_speed_horizontal
        crossing_time_to_conflict_s = target_prediction_time_s - crossing_stabilization_duration_s - crossing_path_lead_time_s
        if crossing_time_to_conflict_s < 3.0 then
            crossing_time_to_conflict_s = 3.0
            target_prediction_time_s = crossing_time_to_conflict_s + crossing_stabilization_duration_s + crossing_path_lead_time_s
        end
    end

    crossing_start_distance_m = crossing_speed_mps * crossing_time_to_conflict_s + crossing_spawn_extra_distance_m
    crossing_effective_speed_mps = crossing_start_distance_m / crossing_time_to_conflict_s
    crossing_target_x = local_x + predicted_dx
    crossing_target_z = local_z + predicted_dz
    intruder_start_y = local_y
    crossing_vertical_speed_mps = study_clamp(
        local_vy * crossing_vertical_match_gain,
        -crossing_vertical_speed_limit_mps,
        crossing_vertical_speed_limit_mps
    )

    if crossing_approach_mode == "front" then
        intruder_start_x = crossing_target_x + intruder_forward_x * crossing_start_distance_m
        intruder_start_z = crossing_target_z + intruder_forward_z * crossing_start_distance_m
    else
        intruder_start_x = crossing_target_x + intruder_right_x * intruder_side_sign * crossing_start_distance_m
        intruder_start_z = crossing_target_z + intruder_right_z * intruder_side_sign * crossing_start_distance_m
    end

    local to_target_x = crossing_target_x - intruder_start_x
    local to_target_z = crossing_target_z - intruder_start_z
    local to_target_len = math.sqrt(to_target_x * to_target_x + to_target_z * to_target_z)
    if to_target_len < 1.0 then
        to_target_len = 1.0
    end

    crossing_move_x = to_target_x / to_target_len
    crossing_move_z = to_target_z / to_target_len
    intruder_heading_deg = study_heading_from_vector(crossing_move_x, crossing_move_z)

    local ok = study_set_intruder_state(
        intruder_start_x,
        intruder_start_y,
        intruder_start_z,
        0.0,
        0.0,
        0.0
    )

    if not ok then
        study_status_text = "CROSSING INTRUDER SET FAILED"
        intruder_crossing_active = false
        crossing_stabilizing = false
        return
    end

    study_status_text = "CROSSING INTRUDER STABILIZING"
    local actual_audio_to_spawn_s = running_time_sec - task_audio_start_sim_time_s
    crossing_spawn_event_detail = study_trial_detail(string.format("scenario=random_final_approach_crossing;profile=realistic_ttc_v13_near_path_intrusion;spawn_phase=stabilization_start;delay_anchor=task_audio_start;planned_audio_to_spawn_s=%.3f;actual_audio_to_spawn_s=%.3f;approach=%s;side_sign=%.0f;planned_move_to_path_s=%.3f;ownship_arrival_to_path_s=%.3f;path_lead_time_s=%.3f;target_ahead_m=%.3f;start_distance_m=%.3f;spawn_extra_distance_m=%.3f;base_speed_mps=%.3f;effective_speed_mps=%.3f;vertical_speed_mps=%.3f;vertical_match_gain=%.3f;stabilization_s=%.3f;heading_deg=%.3f", crossing_planned_audio_to_spawn_s, actual_audio_to_spawn_s, crossing_approach_mode, intruder_side_sign, crossing_time_to_conflict_s, target_prediction_time_s, crossing_path_lead_time_s, predicted_distance, crossing_start_distance_m, crossing_spawn_extra_distance_m, crossing_speed_mps, crossing_effective_speed_mps, crossing_vertical_speed_mps, crossing_vertical_match_gain, crossing_stabilization_duration_s, intruder_heading_deg))
    crossing_spawn_event_recorded = true
    study_record_event("INTRUDER_SPAWNED", crossing_spawn_event_detail)
    logMsg("[StudyIntegrated] crossing intruder spawned and stabilizing")
end

function study_update_random_crossing()
    if random_crossing_armed and trial_active and running_time_sec >= crossing_trigger_time_s then
        study_start_intruder_crossing()
    end

    if not intruder_crossing_active then
        return
    end

    if crossing_stabilizing then
        local ok = study_set_intruder_state(intruder_start_x, intruder_start_y, intruder_start_z, 0.0, 0.0, 0.0)
        if not ok then
            intruder_crossing_active = false
            crossing_stabilizing = false
            study_status_text = "CROSSING DATAREF WRITE FAILED"
            return
        end

        if running_time_sec - crossing_stabilization_start_time_s < crossing_stabilization_duration_s then
            return
        end

        crossing_stabilizing = false
        intruder_start_time_s = running_time_sec
        study_status_text = "CROSSING INTRUDER ACTIVE"
        logMsg("[StudyIntegrated] crossing intruder active after stabilization")
    end

    local elapsed_s = running_time_sec - intruder_start_time_s
    if elapsed_s < 0.0 then
        elapsed_s = 0.0
    end

    local travel_m = crossing_effective_speed_mps * elapsed_s
    local intruder_x = intruder_start_x + crossing_move_x * travel_m
    local intruder_y = intruder_start_y + crossing_vertical_speed_mps * elapsed_s
    local intruder_z = intruder_start_z + crossing_move_z * travel_m

    local vx = crossing_move_x * crossing_effective_speed_mps
    local vy = crossing_vertical_speed_mps
    local vz = crossing_move_z * crossing_effective_speed_mps

    local ok = study_set_intruder_state(intruder_x, intruder_y, intruder_z, vx, vy, vz)
    if not ok then
        intruder_crossing_active = false
        study_status_text = "CROSSING DATAREF WRITE FAILED"
        return
    end

    local dx = local_x - intruder_x
    local dz = local_z - intruder_z
    local horizontal_distance = math.sqrt(dx * dx + dz * dz)

    if horizontal_distance < crossing_min_horizontal_distance_m then
        crossing_min_horizontal_distance_m = horizontal_distance
    end

    if elapsed_s >= crossing_time_to_conflict_s and not crossing_min_distance_reported then
        crossing_min_distance_reported = true
        study_record_event("MIN_DISTANCE_REACHED", study_trial_detail(string.format("scenario=random_final_approach_crossing;horizontal_distance=%.3f", crossing_min_horizontal_distance_m)))
    end

    if elapsed_s > crossing_max_duration_s then
        intruder_crossing_active = false
        study_status_text = "CROSSING INTRUDER PASSED"
        logMsg("[StudyIntegrated] crossing intruder passed")
    end
end

function study_stop_intruder_headon()
    intruder_headon_active = false
    study_status_text = "HEAD-ON INTRUDER OFF"
    study_record_event("MANUAL_NOTE", study_trial_detail("headon_intruder_stopped"))
    logMsg("[StudyIntegrated] head-on intruder stopped")
end

function study_update_intruder_headon()
    if not intruder_headon_active then
        return
    end

    local elapsed_s = running_time_sec - intruder_start_time_s
    if elapsed_s < 0.0 then
        elapsed_s = 0.0
    end

    local travel_m = intruder_speed_mps * elapsed_s
    local intruder_x = intruder_start_x - intruder_forward_x * travel_m
    local intruder_y = intruder_start_y
    local intruder_z = intruder_start_z - intruder_forward_z * travel_m

    local vx = -intruder_forward_x * intruder_speed_mps
    local vy = 0.0
    local vz = -intruder_forward_z * intruder_speed_mps

    local ok = study_set_intruder_state(intruder_x, intruder_y, intruder_z, vx, vy, vz)
    if not ok then
        intruder_headon_active = false
        study_status_text = "INTRUDER DATAREF WRITE FAILED"
        return
    end

    local dx = local_x - intruder_x
    local dz = local_z - intruder_z
    local horizontal_distance = math.sqrt(dx * dx + dz * dz)

    if horizontal_distance < 25.0 or elapsed_s > intruder_max_duration_s then
        intruder_headon_active = false
        study_status_text = "HEAD-ON INTRUDER COMPLETE"
        study_record_event("MIN_DISTANCE_REACHED", study_trial_detail(string.format("horizontal_distance=%.3f", horizontal_distance)))
        logMsg("[StudyIntegrated] head-on intruder completed")
    end
end

function study_start_scenario()
    study_scenario_active = true

    local changed = study_try_set_cloud_condition(true)
    if changed then
        study_status_text = "SCENARIO ACTIVE: CLOUD DECK 1000FT"
    else
        study_status_text = "SCENARIO ACTIVE, CLOUD CHANGE FAILED"
    end

    study_record_event("SCENARIO_MARKER", study_trial_detail("scenario=cloud_deck;base_ft=1000;top_ft=12000;coverage=overcast"))
    study_record_event("ADVISORY_SHOWN", study_trial_detail("scenario=cloud_deck;base_ft=1000;top_ft=12000;coverage=overcast"))
end

function study_stop_scenario()
    study_scenario_active = false

    local changed = study_try_set_cloud_condition(false)
    if changed then
        study_status_text = "SCENARIO OFF"
    else
        study_status_text = "SCENARIO OFF, CLOUD RESET FAILED"
    end

    study_record_event("ADVISORY_CLEARED", study_trial_detail("scenario=cloud_deck"))
end

function study_toggle_cloud_condition()
    if study_scenario_active then
        study_scenario_active = false

        local changed = study_try_set_cloud_condition(false)
        if changed then
            study_status_text = "CLOUD CONDITION OFF"
        else
            study_status_text = "CLOUD OFF FAILED"
        end

        study_record_event("SCENARIO_MARKER", study_trial_detail("cloud_condition=off;scenario=cloud_deck"))
        return
    end

    study_scenario_active = true

    local changed = study_try_set_cloud_condition(true)
    if changed then
        study_status_text = "CLOUD DECK ON: 1000FT+"
    else
        study_status_text = "CLOUD ON FAILED"
    end

    study_record_event("SCENARIO_MARKER", study_trial_detail("cloud_condition=on;base_ft=1000;top_ft=12000;coverage=overcast"))
end

function study_manual_note()
    study_record_event("MANUAL_NOTE", study_trial_detail())
    study_status_text = "MANUAL NOTE EVENT SENT"
end

function study_sample_and_send()
    frame_count = frame_count + 1

    if frame_count % sample_every_n_frames ~= 0 then
        return
    end

    if running_time_sec == last_sent_sim_time_s then
        return
    end

    last_sent_sim_time_s = running_time_sec

    sample_index = sample_index + 1
    last_state_line = study_build_state_line()
    last_intruder_line = study_build_intruder_line()

    study_send_udp(last_state_line)
    study_send_udp(last_intruder_line)
end

function study_frame_update()
    study_update_task_audio()
    study_update_intruder_headon()
    study_update_random_crossing()
    study_sample_and_send()
    study_update_visual_advisory_state()
end

function study_toggle_operator_overlay()
    if trial_active then
        logMsg("[StudyIntegrated] ignored operator overlay toggle during active trial")
        return
    end

    operator_overlay_visible = not operator_overlay_visible
    logMsg("[StudyIntegrated] operator overlay visible: " .. tostring(operator_overlay_visible))
end

function study_draw_status()
    if not operator_overlay_visible then
        local participant_display_text = participant_trial_state_text
        if running_time_sec < participant_trial_notice_until_s then
            participant_display_text = participant_trial_notice_text
        end
        draw_string_Helvetica_18(40, 700, participant_display_text)
        return
    end

    if visual_advisory_active then
        draw_string_Helvetica_18(780, 930, "TRAFFIC ALERT")
        draw_string_Helvetica_18(760, 906, "CHECK OUTSIDE")
    end

    draw_string_Helvetica_18(40, 700, study_status_text)
    draw_string_Helvetica_12(40, 680, string.format("UDP %s:%d", TARGET_HOST, TARGET_PORT))
    draw_string_Helvetica_12(40, 662, string.format("Sample %d | SimTime %.2f | Frame %d", sample_index, running_time_sec, frame_count))
    draw_string_Helvetica_12(40, 644, string.format("Own X %.1f Y %.1f Z %.1f", local_x, local_y, local_z))
    draw_string_Helvetica_12(40, 626, string.format("P1  X %.1f Y %.1f Z %.1f", plane1_x, plane1_y, plane1_z))
    draw_string_Helvetica_12(40, 608, string.format("Haz Dist %.1f | Vert %.1f", last_horizontal_distance, last_vertical_separation))
    draw_string_Helvetica_12(40, 590, string.format("Last event: %s | Count: %d", study_last_event_name, study_event_counter))
    draw_string_Helvetica_12(40, 572, string.format("Trial %d | Active %s", trial_id, tostring(trial_active)))

    if study_scenario_active then
        draw_string_Helvetica_18(40, 546, "SCENARIO ACTIVE")
        draw_string_Helvetica_18(40, 522, "CLOUD / VISIBILITY INTERFERENCE")
    end

    if intruder_headon_active then
        draw_string_Helvetica_18(40, 498, "HEAD-ON INTRUDER ACTIVE")
    end

    if random_crossing_armed then
        draw_string_Helvetica_18(40, 474, string.format("RANDOM CROSSING IN %.1fs", crossing_trigger_time_s - running_time_sec))
    end

    if intruder_crossing_active then
        draw_string_Helvetica_18(40, 450, "CROSSING INTRUDER ACTIVE")
    end
end

create_command(
    "flywithlua/study/reset_trial",
    "Study Reset Trial",
    "study_reset_trial()",
    "",
    ""
)

create_command(
    "flywithlua/study/start_trial",
    "Study Start Trial",
    "study_start_trial()",
    "",
    ""
)

create_command(
    "flywithlua/study/end_trial",
    "Study End Trial",
    "study_end_trial()",
    "",
    ""
)

create_command(
    "flywithlua/study/start_intruder_headon",
    "Study Start Head-On Intruder",
    "study_start_intruder_headon()",
    "",
    ""
)

create_command(
    "flywithlua/study/toggle_cloud",
    "Study Toggle Cloud",
    "study_toggle_cloud_condition()",
    "",
    ""
)

create_command(
    "flywithlua/study/toggle_operator_overlay",
    "Study Toggle Operator Overlay",
    "study_toggle_operator_overlay()",
    "",
    ""
)

create_command(
    "flywithlua/study/test_task_audio",
    "Study Test Task Audio",
    "study_test_task_audio()",
    "",
    ""
)

add_macro("Study Reset Trial", "study_reset_trial()")
add_macro("Study Start Trial", "study_start_trial()")
add_macro("Study End Trial", "study_end_trial()")
add_macro("Study Start Head-On Intruder", "study_start_intruder_headon()")
add_macro("Study Toggle Cloud", "study_toggle_cloud_condition()")
add_macro("Study Toggle Operator Overlay", "study_toggle_operator_overlay()")
add_macro("Study Test Task Audio", "study_test_task_audio()")

do_every_frame("study_frame_update()")
do_every_draw("study_draw_status()")
