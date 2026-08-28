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
local STUDY_SCRIPT_VERSION = "260803_visual_detectability_gate_v32"
local PARTICIPANT_NOTICE_DURATION_S = 2.5
local TASK_AUDIO_PROTOCOL = "audio_task_v32"
local TASK_AUDIO_BASELINE_DELAY_S = 3.0
local AUTO_SESSION_TRIAL_COUNT = 6
local AUTO_SESSION_WAIT_MIN_S = 10.0
local AUTO_SESSION_WAIT_MAX_S = 15.0
local AUTO_SESSION_POST_CONFLICT_CLEAR_S = 2.0
local ATTITUDE_STABILITY_DURATION_S = 3.0
local ATTITUDE_MAX_ABS_ROLL_DEG = 7.0
local ATTITUDE_MAX_ROLL_RANGE_DEG = 3.0
local ATTITUDE_MAX_PITCH_RANGE_DEG = 3.0
local ATTITUDE_MAX_HEADING_RANGE_DEG = 5.0
local TASK_AUDIO_OPTIONS = {
    {
        target_speed_kias = 90,
        file_name = "apisat_task_maintain_90kias_centerline_en_us.wav",
        duration_s = 6.413379
    },
    {
        target_speed_kias = 95,
        file_name = "apisat_task_maintain_95kias_centerline_en_us.wav",
        duration_s = 6.383447
    },
    {
        target_speed_kias = 100,
        file_name = "apisat_task_maintain_100kias_centerline_en_us.wav",
        duration_s = 6.683401
    },
    {
        target_speed_kias = 105,
        file_name = "apisat_task_maintain_105kias_centerline_en_us.wav",
        duration_s = 6.663447
    },
    {
        target_speed_kias = 110,
        file_name = "apisat_task_maintain_110kias_centerline_en_us.wav",
        duration_s = 6.583401
    }
}
local CLOUD_LAYER_BASE_MSL_M = 304.8
local CLOUD_LAYER_TOP_MSL_M = 3657.6
local CLOUD_LAYER_COVERAGE = 5
local METERS_PER_DEG_LAT = 111320.0
local VISUAL_ADVISORY_HAZARD_HORIZONTAL_M = 150.0
local VISUAL_ADVISORY_HAZARD_VERTICAL_M = 30.0
local VISUAL_ADVISORY_CLEAR_HORIZONTAL_M = 200.0
local VISUAL_ADVISORY_CLEAR_VERTICAL_M = 50.0
local STUDY_DISPLAY_WIDTH_PX = 5760.0
local STUDY_DISPLAY_HEIGHT_PX = 1080.0
local STUDY_HORIZONTAL_FOV_DEG = 122.0
local INTRUDER_MODEL_WINGSPAN_M = 11.0
local INTRUDER_MODEL_LENGTH_M = 8.3
local INTRUDER_VISUAL_DETECTABILITY_THRESHOLD_PX = 20.0

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
study_auto_session_active = false
study_auto_session_sequence = 0
study_auto_session_state = "IDLE"
study_auto_session_trial_index = 0
study_auto_session_completed_trials = 0
study_auto_session_next_earliest_s = 0.0
study_auto_session_wait_duration_s = 0.0
study_auto_session_approach_schedule = {}
study_auto_session_speed_schedule = {}
study_attitude_stable_since_s = -1.0
study_attitude_reference_heading_deg = 0.0
study_attitude_min_heading_delta_deg = 0.0
study_attitude_max_heading_delta_deg = 0.0
study_attitude_min_pitch_deg = 0.0
study_attitude_max_pitch_deg = 0.0
study_attitude_min_roll_deg = 0.0
study_attitude_max_roll_deg = 0.0
study_attitude_max_abs_roll_deg = 0.0
study_attitude_gate_passed = false

local task_audio_all_ready = true
local task_audio_current_option = nil
for _, option in ipairs(TASK_AUDIO_OPTIONS) do
    option.wav_path = SCRIPT_DIRECTORY .. "AISimulationProjectAssets/" .. option.file_name
    local task_audio_file = io.open(option.wav_path, "rb")
    if task_audio_file then
        task_audio_file:close()
        option.sound = load_WAV_file(option.wav_path)
        if option.sound == nil then
            task_audio_all_ready = false
            logMsg("[StudyIntegrated] task audio load failed: " .. option.wav_path)
        end
    else
        option.sound = nil
        task_audio_all_ready = false
        logMsg("[StudyIntegrated] task audio missing: " .. option.wav_path)
    end
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
crossing_time_to_conflict_min_s = 16.0
crossing_time_to_conflict_max_s = 20.0
crossing_min_target_ahead_m = 920.0
crossing_max_target_ahead_m = 1100.0
crossing_speed_mps = 58.0
crossing_effective_speed_mps = 58.0
crossing_spawn_extra_distance_m = 500.0
crossing_path_lead_time_s = 0.15
crossing_max_duration_s = 50.0
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
crossing_visual_detectability_recorded = false
crossing_visual_last_valid = false
crossing_visual_last_visible = false
crossing_visual_last_span_px = 0.0
crossing_visual_last_screen_x_px = 0.0
crossing_visual_last_screen_y_px = 0.0
crossing_visual_last_slant_distance_m = 0.0
crossing_visual_last_relative_bearing_deg = 0.0
crossing_visual_last_relative_elevation_deg = 0.0

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
    if study_auto_session_active then
        base = base
            .. ";session_mode=auto_six_trial"
            .. ";session_sequence=" .. tostring(study_auto_session_sequence)
            .. ";session_trial_index=" .. tostring(study_auto_session_trial_index)
            .. ";session_trial_count=" .. tostring(AUTO_SESSION_TRIAL_COUNT)
    end
    if extra == nil or extra == "" then
        return base
    end
    return base .. ";" .. extra
end

function study_normalize_signed_angle_deg(angle_deg)
    local normalized = (angle_deg + 180.0) % 360.0 - 180.0
    return normalized
end

function study_compute_intruder_visual_projection(intruder_x, intruder_y, intruder_z)
    local dx = intruder_x - local_x
    local dy = intruder_y - local_y
    local dz = intruder_z - local_z
    local horizontal_distance_m = math.sqrt(dx * dx + dz * dz)
    local slant_distance_m = math.sqrt(horizontal_distance_m * horizontal_distance_m + dy * dy)

    local own_heading_rad = math.rad(psi_deg)
    local own_forward_x = math.sin(own_heading_rad)
    local own_forward_z = -math.cos(own_heading_rad)
    local own_right_x = math.cos(own_heading_rad)
    local own_right_z = math.sin(own_heading_rad)
    local horizontal_forward_m = dx * own_forward_x + dz * own_forward_z
    local camera_right_m = dx * own_right_x + dz * own_right_z

    local pitch_rad = math.rad(theta_deg)
    local camera_depth_m = horizontal_forward_m * math.cos(pitch_rad) + dy * math.sin(pitch_rad)
    local camera_up_m = dy * math.cos(pitch_rad) - horizontal_forward_m * math.sin(pitch_rad)
    local half_horizontal_fov_rad = math.rad(STUDY_HORIZONTAL_FOV_DEG * 0.5)
    local focal_length_px = STUDY_DISPLAY_WIDTH_PX / (2.0 * math.tan(half_horizontal_fov_rad))

    local intruder_right_x = -crossing_move_z
    local intruder_right_z = crossing_move_x
    local wing_screen_factor = math.abs(intruder_right_x * own_right_x + intruder_right_z * own_right_z)
    local fuselage_screen_factor = math.abs(crossing_move_x * own_right_x + crossing_move_z * own_right_z)
    local estimated_horizontal_span_m = INTRUDER_MODEL_WINGSPAN_M * wing_screen_factor
        + INTRUDER_MODEL_LENGTH_M * fuselage_screen_factor

    local intruder_heading_to_own_deg = study_heading_from_vector(dx, dz)
    local relative_bearing_deg = study_normalize_signed_angle_deg(intruder_heading_to_own_deg - psi_deg)
    local relative_elevation_deg = -theta_deg
    if horizontal_distance_m > 0.001 then
        relative_elevation_deg = math.deg(math.atan(dy / horizontal_distance_m)) - theta_deg
    end

    if camera_depth_m <= 1.0 then
        return {
            valid = false,
            visible = false,
            span_px = 0.0,
            screen_x_px = 0.0,
            screen_y_px = 0.0,
            slant_distance_m = slant_distance_m,
            horizontal_distance_m = horizontal_distance_m,
            relative_bearing_deg = relative_bearing_deg,
            relative_elevation_deg = relative_elevation_deg
        }
    end

    local screen_x_px = STUDY_DISPLAY_WIDTH_PX * 0.5 + focal_length_px * camera_right_m / camera_depth_m
    local screen_y_px = STUDY_DISPLAY_HEIGHT_PX * 0.5 - focal_length_px * camera_up_m / camera_depth_m
    local estimated_span_px = focal_length_px * estimated_horizontal_span_m / camera_depth_m
    local visible = screen_x_px >= 0.0
        and screen_x_px <= STUDY_DISPLAY_WIDTH_PX
        and screen_y_px >= 0.0
        and screen_y_px <= STUDY_DISPLAY_HEIGHT_PX

    return {
        valid = true,
        visible = visible,
        span_px = estimated_span_px,
        screen_x_px = screen_x_px,
        screen_y_px = screen_y_px,
        slant_distance_m = slant_distance_m,
        horizontal_distance_m = horizontal_distance_m,
        relative_bearing_deg = relative_bearing_deg,
        relative_elevation_deg = relative_elevation_deg
    }
end

function study_update_crossing_visual_detectability(intruder_x, intruder_y, intruder_z)
    local projection = study_compute_intruder_visual_projection(intruder_x, intruder_y, intruder_z)
    crossing_visual_last_valid = projection.valid
    crossing_visual_last_visible = projection.visible
    crossing_visual_last_span_px = projection.span_px
    crossing_visual_last_screen_x_px = projection.screen_x_px
    crossing_visual_last_screen_y_px = projection.screen_y_px
    crossing_visual_last_slant_distance_m = projection.slant_distance_m
    crossing_visual_last_relative_bearing_deg = projection.relative_bearing_deg
    crossing_visual_last_relative_elevation_deg = projection.relative_elevation_deg

    if crossing_visual_detectability_recorded
        or not projection.valid
        or not projection.visible
        or projection.span_px < INTRUDER_VISUAL_DETECTABILITY_THRESHOLD_PX then
        return
    end

    crossing_visual_detectability_recorded = true
    local spawn_to_detectable_s = math.max(0.0, running_time_sec - crossing_stabilization_start_time_s)
    study_record_event(
        "INTRUDER_VISUALLY_DETECTABLE",
        study_trial_detail(
            string.format(
                "method=perspective_bbox_proxy_v1;operational_proxy=true;display_width_px=%.0f;display_height_px=%.0f;horizontal_fov_deg=%.3f;threshold_px=%.3f;estimated_span_px=%.3f;screen_x_px=%.3f;screen_y_px=%.3f;slant_distance_m=%.3f;horizontal_distance_m=%.3f;relative_bearing_deg=%.3f;relative_elevation_deg=%.3f;spawn_to_detectable_s=%.3f;roll_correction=ignored",
                STUDY_DISPLAY_WIDTH_PX,
                STUDY_DISPLAY_HEIGHT_PX,
                STUDY_HORIZONTAL_FOV_DEG,
                INTRUDER_VISUAL_DETECTABILITY_THRESHOLD_PX,
                projection.span_px,
                projection.screen_x_px,
                projection.screen_y_px,
                projection.slant_distance_m,
                projection.horizontal_distance_m,
                projection.relative_bearing_deg,
                projection.relative_elevation_deg,
                spawn_to_detectable_s
            )
            .. ";"
            .. study_ownship_context_detail()
        )
    )
end

function study_ownship_context_detail()
    return string.format(
        "own_latitude_deg=%.8f;own_longitude_deg=%.8f;own_elevation_m=%.3f;own_agl_m=%.3f;own_heading_deg=%.3f;own_pitch_deg=%.3f;own_roll_deg=%.3f;own_p_rate=%.3f;own_q_rate=%.3f;own_r_rate=%.3f;own_ias_kias=%.3f;own_tas_mps=%.3f;own_vertical_speed_mps=%.3f;own_pitch_input=%.4f;own_roll_input=%.4f;own_yaw_input=%.4f;own_throttle_input=%.4f",
        latitude_deg,
        longitude_deg,
        elevation_m,
        y_agl_m,
        psi_deg,
        theta_deg,
        phi_deg,
        p_rate,
        q_rate,
        r_rate,
        ias_mps,
        tas_mps,
        vertical_speed_mps,
        yoke_pitch_ratio,
        yoke_roll_ratio,
        yoke_heading_ratio,
        throttle_ratio_all
    )
end

function study_random_between(min_value, max_value)
    return min_value + math.random() * (max_value - min_value)
end

function study_shuffle_table(values)
    for index = #values, 2, -1 do
        local swap_index = math.random(1, index)
        values[index], values[swap_index] = values[swap_index], values[index]
    end
end

function study_schedule_text(values)
    local parts = {}
    for index, value in ipairs(values) do
        parts[index] = tostring(value)
    end
    return table.concat(parts, "|")
end

function study_heading_delta_deg(value_deg, reference_deg)
    local delta = (value_deg - reference_deg + 180.0) % 360.0 - 180.0
    return delta
end

function study_reset_attitude_stability_window()
    study_attitude_stable_since_s = -1.0
    study_attitude_reference_heading_deg = psi_deg
    study_attitude_min_heading_delta_deg = 0.0
    study_attitude_max_heading_delta_deg = 0.0
    study_attitude_min_pitch_deg = theta_deg
    study_attitude_max_pitch_deg = theta_deg
    study_attitude_min_roll_deg = phi_deg
    study_attitude_max_roll_deg = phi_deg
    study_attitude_max_abs_roll_deg = math.abs(phi_deg)
    study_attitude_gate_passed = false
end

function study_start_attitude_stability_window()
    study_attitude_stable_since_s = running_time_sec
    study_attitude_reference_heading_deg = psi_deg
    study_attitude_min_heading_delta_deg = 0.0
    study_attitude_max_heading_delta_deg = 0.0
    study_attitude_min_pitch_deg = theta_deg
    study_attitude_max_pitch_deg = theta_deg
    study_attitude_min_roll_deg = phi_deg
    study_attitude_max_roll_deg = phi_deg
    study_attitude_max_abs_roll_deg = math.abs(phi_deg)
    study_attitude_gate_passed = false
end

function study_update_attitude_stability()
    if math.abs(phi_deg) > ATTITUDE_MAX_ABS_ROLL_DEG then
        study_reset_attitude_stability_window()
        return false
    end

    if study_attitude_stable_since_s < 0.0 or running_time_sec < study_attitude_stable_since_s then
        study_start_attitude_stability_window()
        return false
    end

    local heading_delta = study_heading_delta_deg(psi_deg, study_attitude_reference_heading_deg)
    study_attitude_min_heading_delta_deg = math.min(study_attitude_min_heading_delta_deg, heading_delta)
    study_attitude_max_heading_delta_deg = math.max(study_attitude_max_heading_delta_deg, heading_delta)
    study_attitude_min_pitch_deg = math.min(study_attitude_min_pitch_deg, theta_deg)
    study_attitude_max_pitch_deg = math.max(study_attitude_max_pitch_deg, theta_deg)
    study_attitude_min_roll_deg = math.min(study_attitude_min_roll_deg, phi_deg)
    study_attitude_max_roll_deg = math.max(study_attitude_max_roll_deg, phi_deg)
    study_attitude_max_abs_roll_deg = math.max(study_attitude_max_abs_roll_deg, math.abs(phi_deg))

    local heading_range = study_attitude_max_heading_delta_deg - study_attitude_min_heading_delta_deg
    local pitch_range = study_attitude_max_pitch_deg - study_attitude_min_pitch_deg
    local roll_range = study_attitude_max_roll_deg - study_attitude_min_roll_deg
    if heading_range > ATTITUDE_MAX_HEADING_RANGE_DEG
        or pitch_range > ATTITUDE_MAX_PITCH_RANGE_DEG
        or roll_range > ATTITUDE_MAX_ROLL_RANGE_DEG then
        study_start_attitude_stability_window()
        return false
    end

    study_attitude_gate_passed = running_time_sec - study_attitude_stable_since_s >= ATTITUDE_STABILITY_DURATION_S
    return study_attitude_gate_passed
end

function study_attitude_stability_detail()
    return string.format(
        "stability_duration_s=%.3f;max_abs_roll_deg=%.3f;roll_range_deg=%.3f;pitch_range_deg=%.3f;heading_range_deg=%.3f",
        math.max(0.0, running_time_sec - study_attitude_stable_since_s),
        study_attitude_max_abs_roll_deg,
        study_attitude_max_roll_deg - study_attitude_min_roll_deg,
        study_attitude_max_pitch_deg - study_attitude_min_pitch_deg,
        study_attitude_max_heading_delta_deg - study_attitude_min_heading_delta_deg
    )
end

function study_arm_task_audio()
    task_audio_armed = true
    task_audio_active = false
    task_audio_trigger_time_s = running_time_sec + TASK_AUDIO_BASELINE_DELAY_S
    task_audio_start_sim_time_s = 0.0
    task_audio_start_wall_time_s = 0.0
    task_audio_end_wall_time_s = 0.0
end

function study_select_task_audio()
    if study_auto_session_active
        and study_auto_session_trial_index >= 1
        and study_auto_session_speed_schedule[study_auto_session_trial_index] ~= nil then
        task_audio_current_option = TASK_AUDIO_OPTIONS[study_auto_session_speed_schedule[study_auto_session_trial_index]]
    else
        task_audio_current_option = TASK_AUDIO_OPTIONS[math.random(1, #TASK_AUDIO_OPTIONS)]
    end
    return task_audio_current_option
end

function study_start_task_audio()
    if not trial_active or not task_audio_armed then
        return
    end
    if task_audio_current_option == nil then
        study_select_task_audio()
    end

    task_audio_armed = false
    task_audio_start_sim_time_s = running_time_sec
    task_audio_start_wall_time_s = socket.gettime()
    task_audio_end_wall_time_s = task_audio_start_wall_time_s + task_audio_current_option.duration_s
    study_record_event(
        "TASK_COMMAND_AUDIO_START",
        study_trial_detail(
            string.format(
                "protocol=%s;target_speed_kias=%d;file=%s;scheduled_duration_s=%.6f",
                TASK_AUDIO_PROTOCOL,
                task_audio_current_option.target_speed_kias,
                task_audio_current_option.file_name,
                task_audio_current_option.duration_s
            )
        )
    )

    local ok, err = pcall(function()
        play_sound(task_audio_current_option.sound)
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
    logMsg(
        string.format(
            "[StudyIntegrated] task audio started: %d KIAS, %s",
            task_audio_current_option.target_speed_kias,
            task_audio_current_option.file_name
        )
    )
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
                "protocol=%s;target_speed_kias=%d;file=%s;scheduled_duration_s=%.6f;actual_wall_duration_s=%.3f",
                TASK_AUDIO_PROTOCOL,
                task_audio_current_option.target_speed_kias,
                task_audio_current_option.file_name,
                task_audio_current_option.duration_s,
                actual_duration_s
            )
        )
    )
    logMsg(
        string.format(
            "[StudyIntegrated] task audio ended: %d KIAS, %s",
            task_audio_current_option.target_speed_kias,
            task_audio_current_option.file_name
        )
    )
end

function study_test_task_audio()
    if trial_active or study_auto_session_active then
        logMsg("[StudyIntegrated] ignored task audio test during active session")
        return
    end
    if not task_audio_all_ready then
        participant_trial_notice_text = "TASK AUDIO NOT READY"
        participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
        logMsg("[StudyIntegrated] task audio test failed; one or more assets not loaded")
        return
    end

    local test_option = study_select_task_audio()
    local ok, err = pcall(function()
        play_sound(test_option.sound)
    end)
    if not ok then
        participant_trial_notice_text = "TASK AUDIO ERROR"
        participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
        logMsg("[StudyIntegrated] task audio test failed: " .. tostring(err))
        return
    end

    participant_trial_notice_text = string.format("TASK AUDIO TEST: %d KT", test_option.target_speed_kias)
    participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
    logMsg(
        string.format(
            "[StudyIntegrated] task audio test started: %d KIAS, %s",
            test_option.target_speed_kias,
            test_option.file_name
        )
    )
end

function study_reset_trial(automatic)
    if study_auto_session_active and automatic ~= true then
        participant_trial_notice_text = "AUTO SESSION ACTIVE"
        participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
        study_record_event("MANUAL_NOTE", study_trial_detail("ignored=manual_reset_during_auto_session"))
        return
    end

    trial_id = trial_id + 1
    trial_active = false
    trial_start_time_s = 0.0
    task_audio_armed = false
    task_audio_active = false
    task_audio_trigger_time_s = 0.0
    task_audio_start_sim_time_s = 0.0
    task_audio_start_wall_time_s = 0.0
    task_audio_end_wall_time_s = 0.0
    task_audio_current_option = nil
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
    crossing_visual_detectability_recorded = false
    crossing_visual_last_valid = false
    crossing_visual_last_visible = false
    crossing_visual_last_span_px = 0.0
    crossing_visual_last_screen_x_px = 0.0
    crossing_visual_last_screen_y_px = 0.0
    crossing_visual_last_slant_distance_m = 0.0
    crossing_visual_last_relative_bearing_deg = 0.0
    crossing_visual_last_relative_elevation_deg = 0.0
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

function study_start_trial(automatic)
    if study_auto_session_active and automatic ~= true then
        participant_trial_notice_text = "AUTO SESSION ACTIVE"
        participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
        study_record_event("MANUAL_NOTE", study_trial_detail("ignored=manual_start_during_auto_session"))
        return
    end

    if trial_active then
        study_status_text = "TRIAL ALREADY ACTIVE"
        study_record_event("MANUAL_NOTE", study_trial_detail("ignored=duplicate_trial_start"))
        return
    end

    if not task_audio_all_ready then
        participant_trial_notice_text = "TASK AUDIO NOT READY"
        participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
        study_status_text = "TASK AUDIO NOT READY"
        study_record_event("MANUAL_NOTE", study_trial_detail("ignored=start_without_task_audio"))
        return
    end

    if trial_id <= 0 then
        study_reset_trial(automatic)
    end

    if task_audio_current_option == nil then
        study_select_task_audio()
    end
    trial_active = true
    trial_start_time_s = running_time_sec
    operator_overlay_visible = false
    participant_trial_notice_text = ""
    participant_trial_notice_until_s = -1.0
    if study_auto_session_active then
        participant_trial_state_text = string.format(
            "TRIAL %d/%d ACTIVE",
            study_auto_session_trial_index,
            AUTO_SESSION_TRIAL_COUNT
        )
    else
        participant_trial_state_text = string.format("TRIAL ACTIVE: %d", trial_id)
    end
    study_status_text = string.format("TRIAL START: %d", trial_id)
    study_record_event(
        "TRIAL_START",
        study_trial_detail(
            string.format(
                "protocol=%s;target_speed_kias=%d;task_audio_file=%s;task_audio_baseline_s=%.3f;scheduled_approach=%s;sound_master=%.3f;sound_interior=%.3f;sound_engine=%.3f;sound_prop=%.3f;sound_enviro=%.3f;sound_radio=%.3f",
                TASK_AUDIO_PROTOCOL,
                task_audio_current_option.target_speed_kias,
                task_audio_current_option.file_name,
                TASK_AUDIO_BASELINE_DELAY_S,
                study_auto_session_active
                    and study_auto_session_approach_schedule[study_auto_session_trial_index]
                    or "random",
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

function study_auto_trial_can_end()
    if not study_trial_can_end() then
        return false
    end
    if not intruder_crossing_active then
        return true
    end

    local elapsed_s = math.max(0.0, running_time_sec - intruder_start_time_s)
    return crossing_min_distance_reported
        and elapsed_s >= crossing_time_to_conflict_s + AUTO_SESSION_POST_CONFLICT_CLEAR_S
        and last_horizontal_distance >= VISUAL_ADVISORY_CLEAR_HORIZONTAL_M
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

function study_end_trial(automatic)
    if study_auto_session_active and automatic ~= true then
        participant_trial_notice_text = "AUTO SESSION ACTIVE"
        participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
        study_record_event("MANUAL_NOTE", study_trial_detail("ignored=manual_end_during_auto_session"))
        return
    end

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
    study_retire_intruder()
    trial_active = false
    participant_trial_notice_text = ""
    participant_trial_notice_until_s = -1.0
    participant_trial_state_text = string.format("TRIAL END: %d", trial_id)
    study_status_text = string.format("TRIAL END: %d", trial_id)
    study_record_event(
        "TRIAL_END",
        study_trial_detail("end_source=" .. (automatic == true and "automatic" or "manual"))
    )

    if study_auto_session_active and automatic == true then
        study_auto_session_after_trial_end()
    end
end

function study_prepare_auto_session_schedules()
    study_auto_session_approach_schedule = {
        "left", "left", "right", "right", "front", "front"
    }
    study_shuffle_table(study_auto_session_approach_schedule)

    study_auto_session_speed_schedule = {1, 2, 3, 4, 5, math.random(1, #TASK_AUDIO_OPTIONS)}
    study_shuffle_table(study_auto_session_speed_schedule)
end

function study_auto_session_speed_text()
    local speeds = {}
    for index, option_index in ipairs(study_auto_session_speed_schedule) do
        speeds[index] = TASK_AUDIO_OPTIONS[option_index].target_speed_kias
    end
    return study_schedule_text(speeds)
end

function study_begin_next_auto_trial()
    if not study_auto_session_active then
        return
    end

    study_auto_session_trial_index = study_auto_session_completed_trials + 1
    if study_auto_session_trial_index > AUTO_SESSION_TRIAL_COUNT then
        return
    end

    local wait_state = study_auto_session_state
    local wait_duration_s = study_auto_session_wait_duration_s
    local stability_detail = study_attitude_stability_detail()
    study_reset_trial(true)
    study_select_task_audio()
    study_record_event(
        "SCENARIO_MARKER",
        study_trial_detail(
            "automatic_session=trial_ready"
            .. ";wait_state=" .. wait_state
            .. string.format(";wait_duration_s=%.3f;", wait_duration_s)
            .. stability_detail
            .. ";scheduled_approach=" .. study_auto_session_approach_schedule[study_auto_session_trial_index]
            .. ";scheduled_speed_kias=" .. tostring(task_audio_current_option.target_speed_kias)
        )
    )
    study_auto_session_state = "TRIAL_ACTIVE"
    study_start_trial(true)
end

function study_auto_session_after_trial_end()
    study_auto_session_completed_trials = study_auto_session_trial_index
    if study_auto_session_completed_trials >= AUTO_SESSION_TRIAL_COUNT then
        study_auto_session_state = "COMPLETE"
        participant_trial_state_text = "SESSION COMPLETE"
        study_status_text = "AUTO SESSION COMPLETE"
        study_record_event(
            "SCENARIO_MARKER",
            study_trial_detail(
                "automatic_session=complete;completed_trials="
                .. tostring(study_auto_session_completed_trials)
            )
        )
        study_auto_session_active = false
        return
    end

    study_auto_session_state = "WASHOUT"
    study_auto_session_wait_duration_s = study_random_between(
        AUTO_SESSION_WAIT_MIN_S,
        AUTO_SESSION_WAIT_MAX_S
    )
    study_auto_session_next_earliest_s = running_time_sec + study_auto_session_wait_duration_s
    study_reset_attitude_stability_window()
    participant_trial_state_text = string.format(
        "SESSION ACTIVE: %d/%d",
        study_auto_session_completed_trials,
        AUTO_SESSION_TRIAL_COUNT
    )
    study_status_text = "AUTO SESSION WASHOUT"
    study_record_event(
        "SCENARIO_MARKER",
        study_trial_detail(
            string.format(
                "automatic_session=washout;completed_trials=%d;next_trial_index=%d;washout_s=%.3f",
                study_auto_session_completed_trials,
                study_auto_session_completed_trials + 1,
                study_auto_session_wait_duration_s
            )
        )
    )
end

function study_start_auto_session()
    if study_auto_session_active then
        participant_trial_notice_text = "SESSION ALREADY ACTIVE"
        participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
        return
    end
    if trial_active then
        participant_trial_notice_text = "END ACTIVE TRIAL FIRST"
        participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
        return
    end
    if not task_audio_all_ready then
        participant_trial_notice_text = "TASK AUDIO NOT READY"
        participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
        return
    end

    study_auto_session_sequence = study_auto_session_sequence + 1
    study_auto_session_active = true
    study_auto_session_state = "INITIAL_WAIT"
    study_auto_session_trial_index = 0
    study_auto_session_completed_trials = 0
    study_prepare_auto_session_schedules()
    study_auto_session_wait_duration_s = study_random_between(
        AUTO_SESSION_WAIT_MIN_S,
        AUTO_SESSION_WAIT_MAX_S
    )
    study_auto_session_next_earliest_s = running_time_sec + study_auto_session_wait_duration_s
    study_reset_attitude_stability_window()
    operator_overlay_visible = false
    participant_trial_notice_text = ""
    participant_trial_notice_until_s = -1.0
    participant_trial_state_text = "SESSION ACTIVE: 0/6"
    study_status_text = "AUTO SESSION INITIAL WAIT"
    study_record_event(
        "SCENARIO_MARKER",
        study_trial_detail(
            "automatic_session=start"
            .. ";approach_schedule=" .. study_schedule_text(study_auto_session_approach_schedule)
            .. ";speed_schedule_kias=" .. study_auto_session_speed_text()
            .. string.format(";initial_wait_s=%.3f", study_auto_session_wait_duration_s)
        )
    )
    logMsg(
        "[StudyIntegrated] automatic session started; approaches="
        .. study_schedule_text(study_auto_session_approach_schedule)
        .. "; speeds=" .. study_auto_session_speed_text()
    )
end

function study_stop_auto_session()
    if not study_auto_session_active then
        participant_trial_notice_text = "NO ACTIVE SESSION"
        participant_trial_notice_until_s = running_time_sec + PARTICIPANT_NOTICE_DURATION_S
        return
    end

    study_record_event(
        "MANUAL_NOTE",
        study_trial_detail(
            "automatic_session=stopped;state=" .. study_auto_session_state
            .. ";completed_trials=" .. tostring(study_auto_session_completed_trials)
        )
    )
    task_audio_armed = false
    task_audio_active = false
    random_crossing_armed = false
    crossing_stabilizing = false
    intruder_crossing_active = false
    intruder_headon_active = false
    visual_advisory_active = false
    study_retire_intruder()
    trial_active = false
    study_auto_session_active = false
    study_auto_session_state = "STOPPED"
    participant_trial_state_text = "SESSION STOPPED"
    study_status_text = "AUTO SESSION STOPPED"
end

function study_update_auto_session()
    if not study_auto_session_active then
        return
    end

    if study_auto_session_state == "TRIAL_ACTIVE" then
        if trial_active and study_auto_trial_can_end() then
            study_end_trial(true)
        end
        return
    end

    if study_auto_session_state ~= "INITIAL_WAIT" and study_auto_session_state ~= "WASHOUT" then
        return
    end

    local attitude_stable = study_update_attitude_stability()
    if running_time_sec >= study_auto_session_next_earliest_s and attitude_stable then
        study_begin_next_auto_trial()
    end
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

function study_retire_intruder()
    return study_set_intruder_state(
        local_x,
        local_y - 3000.0,
        local_z,
        0.0,
        0.0,
        0.0
    )
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
    study_record_event(
        "INTRUDER_SPAWNED",
        study_trial_detail("scenario=headon_intruder;" .. study_ownship_context_detail())
    )
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
    crossing_visual_detectability_recorded = false
    crossing_visual_last_valid = false
    crossing_visual_last_visible = false
    crossing_visual_last_span_px = 0.0
    crossing_visual_last_screen_x_px = 0.0
    crossing_visual_last_screen_y_px = 0.0
    crossing_visual_last_slant_distance_m = 0.0
    crossing_visual_last_relative_bearing_deg = 0.0
    crossing_visual_last_relative_elevation_deg = 0.0
    intruder_start_time_s = running_time_sec + crossing_stabilization_duration_s

    local own_heading_rad = math.rad(psi_deg)
    intruder_forward_x = math.sin(own_heading_rad)
    intruder_forward_z = -math.cos(own_heading_rad)
    intruder_right_x = math.cos(own_heading_rad)
    intruder_right_z = math.sin(own_heading_rad)

    local scheduled_approach = nil
    if study_auto_session_active and study_auto_session_trial_index >= 1 then
        scheduled_approach = study_auto_session_approach_schedule[study_auto_session_trial_index]
    end

    local approach_choice = math.random(1, 3)
    if scheduled_approach == "left" or (scheduled_approach == nil and approach_choice == 1) then
        intruder_side_sign = -1.0
        crossing_approach_mode = "left"
    elseif scheduled_approach == "right" or (scheduled_approach == nil and approach_choice == 2) then
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
    local initial_projection = study_compute_intruder_visual_projection(
        intruder_start_x,
        intruder_start_y,
        intruder_start_z
    )
    crossing_spawn_event_detail = study_trial_detail(
        string.format(
            "scenario=random_final_approach_crossing;profile=visual_detectability_proxy_v32;spawn_phase=stabilization_start;delay_anchor=task_audio_start;planned_audio_to_spawn_s=%.3f;actual_audio_to_spawn_s=%.3f;approach=%s;side_sign=%.0f;planned_move_to_path_s=%.3f;ownship_arrival_to_path_s=%.3f;path_lead_time_s=%.3f;target_ahead_m=%.3f;start_distance_m=%.3f;spawn_extra_distance_m=%.3f;base_speed_mps=%.3f;effective_speed_mps=%.3f;vertical_speed_mps=%.3f;vertical_match_gain=%.3f;stabilization_s=%.3f;heading_deg=%.3f;display_width_px=%.0f;display_height_px=%.0f;horizontal_fov_deg=%.3f;detectability_threshold_px=%.3f;initial_projection_valid=%s;initial_screen_visible=%s;initial_estimated_span_px=%.3f;initial_screen_x_px=%.3f;initial_screen_y_px=%.3f;initial_slant_distance_m=%.3f;initial_relative_bearing_deg=%.3f;initial_relative_elevation_deg=%.3f",
            crossing_planned_audio_to_spawn_s,
            actual_audio_to_spawn_s,
            crossing_approach_mode,
            intruder_side_sign,
            crossing_time_to_conflict_s,
            target_prediction_time_s,
            crossing_path_lead_time_s,
            predicted_distance,
            crossing_start_distance_m,
            crossing_spawn_extra_distance_m,
            crossing_speed_mps,
            crossing_effective_speed_mps,
            crossing_vertical_speed_mps,
            crossing_vertical_match_gain,
            crossing_stabilization_duration_s,
            intruder_heading_deg,
            STUDY_DISPLAY_WIDTH_PX,
            STUDY_DISPLAY_HEIGHT_PX,
            STUDY_HORIZONTAL_FOV_DEG,
            INTRUDER_VISUAL_DETECTABILITY_THRESHOLD_PX,
            tostring(initial_projection.valid),
            tostring(initial_projection.visible),
            initial_projection.span_px,
            initial_projection.screen_x_px,
            initial_projection.screen_y_px,
            initial_projection.slant_distance_m,
            initial_projection.relative_bearing_deg,
            initial_projection.relative_elevation_deg
        )
        .. ";"
        .. study_ownship_context_detail()
    )
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

        study_update_crossing_visual_detectability(
            intruder_start_x,
            intruder_start_y,
            intruder_start_z
        )

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

    study_update_crossing_visual_detectability(intruder_x, intruder_y, intruder_z)

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
    study_update_auto_session()
end

function study_toggle_operator_overlay()
    if trial_active or study_auto_session_active then
        logMsg("[StudyIntegrated] ignored operator overlay toggle during active session")
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
    draw_string_Helvetica_12(
        40,
        536,
        string.format(
            "Visual span %.1f/%.1f px | Screen %.0f,%.0f | Visible %s | Gate %s",
            crossing_visual_last_span_px,
            INTRUDER_VISUAL_DETECTABILITY_THRESHOLD_PX,
            crossing_visual_last_screen_x_px,
            crossing_visual_last_screen_y_px,
            tostring(crossing_visual_last_visible),
            tostring(crossing_visual_detectability_recorded)
        )
    )

    if study_auto_session_active then
        local remaining_wait_s = math.max(0.0, study_auto_session_next_earliest_s - running_time_sec)
        draw_string_Helvetica_12(
            40,
            554,
            string.format(
                "Auto session %d/%d | State %s | Wait %.1fs | Stable %s",
                study_auto_session_completed_trials,
                AUTO_SESSION_TRIAL_COUNT,
                study_auto_session_state,
                remaining_wait_s,
                tostring(study_attitude_gate_passed)
            )
        )
    end

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
    "flywithlua/study/start_auto_session",
    "Study Start Automatic Session",
    "study_start_auto_session()",
    "",
    ""
)

create_command(
    "flywithlua/study/stop_auto_session",
    "Study Stop Automatic Session",
    "study_stop_auto_session()",
    "",
    ""
)

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

add_macro("Study Start Automatic Session", "study_start_auto_session()")
add_macro("Study Stop Automatic Session", "study_stop_auto_session()")
add_macro("Study Reset Trial", "study_reset_trial()")
add_macro("Study Start Trial", "study_start_trial()")
add_macro("Study End Trial", "study_end_trial()")
add_macro("Study Start Head-On Intruder", "study_start_intruder_headon()")
add_macro("Study Toggle Cloud", "study_toggle_cloud_condition()")
add_macro("Study Toggle Operator Overlay", "study_toggle_operator_overlay()")
add_macro("Study Test Task Audio", "study_test_task_audio()")

do_every_frame("study_frame_update()")
do_every_draw("study_draw_status()")
