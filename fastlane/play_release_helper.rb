# Pure Ruby helpers for Play Store version calculation and input validation.
module PlayReleaseHelper
  PLAY_TRACKS = %w[production beta alpha internal].freeze
  REQUIRED_SIGNING_ENV_VARS = %w[
    ANDROID_KEYSTORE_FILE
    ANDROID_KEYSTORE_PASSWORD
    ANDROID_KEY_ALIAS
    ANDROID_KEY_PASSWORD
  ].freeze
  VERSION_CODE_MULTIPLIER = 10_000
  MAX_PLAY_VERSION_CODE = 2_100_000_000

  module_function

  def read_gradle_properties(path)
    raise ArgumentError, "Missing gradle.properties at #{path}" unless File.readable?(path)

    props = {}
    File.foreach(path, encoding: "UTF-8") do |raw_line|
      line = raw_line.strip
      next if line.empty? || line.start_with?("#", "!")

      key, value = line.split("=", 2)
      next unless key && value

      props[key.strip] = value.strip
    end

    version_code_str = props["app.versionCode"]
    version_name = props["app.versionName"]
    unless version_code_str && version_code_str.match?(/\A\d+\z/) && version_code_str.to_i.positive?
      raise ArgumentError, "Invalid or missing app.versionCode in #{path}: #{version_code_str.inspect}"
    end
    if version_name.nil? || version_name.empty?
      raise ArgumentError, "Missing app.versionName in #{path}"
    end

    { version_code: version_code_str.to_i, version_name: version_name }
  end

  def compute_release_version_code(base_version_code:, build_number:, existing_play_codes: [])
    unless base_version_code.is_a?(Integer) && base_version_code.positive?
      raise ArgumentError, "base_version_code must be positive, got #{base_version_code.inspect}"
    end

    parsed_build = build_number.to_i
    effective_build = parsed_build.positive? ? parsed_build : 1
    candidate = base_version_code * VERSION_CODE_MULTIPLIER + effective_build

    max_play = existing_play_codes.compact.map(&:to_i).select(&:positive?).max
    result = max_play ? [candidate, max_play + 1].max : candidate

    if result > MAX_PLAY_VERSION_CODE
      raise RangeError, "Computed versionCode #{result} exceeds Play limit #{MAX_PLAY_VERSION_CODE}"
    end

    result
  end

  def validate_release_inputs!(env:, repo_root:)
    missing = []

    play_json = env["PLAY_SERVICE_ACCOUNT_JSON"].to_s.strip
    if play_json.empty?
      missing << "PLAY_SERVICE_ACCOUNT_JSON env var"
    elsif !File.readable?(play_json)
      missing << "readable file at PLAY_SERVICE_ACCOUNT_JSON (#{play_json})"
    end

    key_props = File.join(repo_root, "key.properties")
    has_key_props = File.readable?(key_props)
    missing_signing_vars = REQUIRED_SIGNING_ENV_VARS.select { |var| env[var].to_s.strip.empty? }
    keystore_file = env["ANDROID_KEYSTORE_FILE"].to_s.strip

    unless has_key_props
      if !missing_signing_vars.empty?
        missing.concat(missing_signing_vars.map { |var| "#{var} env var" })
      elsif !File.readable?(keystore_file)
        missing << "readable file at ANDROID_KEYSTORE_FILE (#{keystore_file})"
      end
    end

    return if missing.empty?

    raise ArgumentError, "Missing Play release configuration: #{missing.join(', ')}"
  end

  # Parses a comma-separated list of Play tracks (e.g. "internal,alpha").
  def parse_tracks(value)
    tracks = value.to_s.split(",").map(&:strip).reject(&:empty?).uniq
    raise ArgumentError, "No Play tracks configured" if tracks.empty?

    tracks
  end

  def resolve_aab_path(bundle_dir)
    candidates = Dir.glob(File.join(bundle_dir, "*.aab"))
    raise ArgumentError, "No .aab found in #{bundle_dir}" if candidates.empty?

    candidates.max_by { |path| File.mtime(path) }
  end
end
