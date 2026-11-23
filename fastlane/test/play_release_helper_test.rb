require "minitest/autorun"
require "tmpdir"
require_relative "../play_release_helper"

class PlayReleaseHelperTest < Minitest::Test
  def test_read_gradle_properties_parses_version_fields
    Dir.mktmpdir do |dir|
      props_file = File.join(dir, "gradle.properties")
      File.write(props_file, <<~PROPS)
        # Comment line
        ! Another comment
        org.gradle.jvmargs=-Xmx4096m
        app.versionName = 0.12.10-beta
        app.versionCode = 84
      PROPS

      result = PlayReleaseHelper.read_gradle_properties(props_file)
      assert_equal({ version_code: 84, version_name: "0.12.10-beta" }, result)
    end
  end

  def test_read_gradle_properties_rejects_missing_or_invalid_values
    Dir.mktmpdir do |dir|
      missing_code = File.join(dir, "missing_code.properties")
      File.write(missing_code, "app.versionName=1.0.0\n")
      assert_raises(ArgumentError) { PlayReleaseHelper.read_gradle_properties(missing_code) }

      bad_code = File.join(dir, "bad_code.properties")
      File.write(bad_code, "app.versionCode=abc\napp.versionName=1.0.0\n")
      assert_raises(ArgumentError) { PlayReleaseHelper.read_gradle_properties(bad_code) }

      missing_name = File.join(dir, "missing_name.properties")
      File.write(missing_name, "app.versionCode=84\n")
      assert_raises(ArgumentError) { PlayReleaseHelper.read_gradle_properties(missing_name) }
    end
  end

  def test_compute_release_version_code_progression_and_clamping
    assert_equal(
      840_005,
      PlayReleaseHelper.compute_release_version_code(
        base_version_code: 84,
        build_number: "5",
        existing_play_codes: [],
      ),
    )

    assert_equal(
      840_006,
      PlayReleaseHelper.compute_release_version_code(
        base_version_code: 84,
        build_number: 6,
        existing_play_codes: [840_005, nil, 830_012],
      ),
    )

    assert_equal(
      850_001,
      PlayReleaseHelper.compute_release_version_code(
        base_version_code: 85,
        build_number: "1",
        existing_play_codes: [840_050],
      ),
    )

    assert_equal(
      900_001,
      PlayReleaseHelper.compute_release_version_code(
        base_version_code: 84,
        build_number: "10",
        existing_play_codes: [840_009, 900_000],
      ),
    )

    assert_equal(
      840_001,
      PlayReleaseHelper.compute_release_version_code(
        base_version_code: 84,
        build_number: "0",
        existing_play_codes: [],
      ),
    )
  end

  def test_compute_release_version_code_guards_bounds
    assert_raises(ArgumentError) do
      PlayReleaseHelper.compute_release_version_code(
        base_version_code: 0,
        build_number: 1,
      )
    end

    assert_raises(RangeError) do
      PlayReleaseHelper.compute_release_version_code(
        base_version_code: 84,
        build_number: 1,
        existing_play_codes: [2_100_000_000],
      )
    end
  end

  def test_validate_release_inputs
    Dir.mktmpdir do |dir|
      play_json = File.join(dir, "play.json")
      keystore = File.join(dir, "upload.jks")
      File.write(play_json, "{}")
      File.write(keystore, "dummy")

      assert_raises(ArgumentError) do
        PlayReleaseHelper.validate_release_inputs!(env: {}, repo_root: dir)
      end

      assert_raises(ArgumentError) do
        PlayReleaseHelper.validate_release_inputs!(
          env: { "PLAY_SERVICE_ACCOUNT_JSON" => play_json },
          repo_root: dir,
        )
      end

      PlayReleaseHelper.validate_release_inputs!(
        env: {
          "PLAY_SERVICE_ACCOUNT_JSON" => play_json,
          "ANDROID_KEYSTORE_FILE" => keystore,
          "ANDROID_KEYSTORE_PASSWORD" => "pass",
          "ANDROID_KEY_ALIAS" => "upload",
          "ANDROID_KEY_PASSWORD" => "pass",
        },
        repo_root: dir,
      )

      File.write(File.join(dir, "key.properties"), "storeFile=#{keystore}\n")
      PlayReleaseHelper.validate_release_inputs!(
        env: { "PLAY_SERVICE_ACCOUNT_JSON" => play_json },
        repo_root: dir,
      )
    end
  end

  def test_resolve_aab_path
    Dir.mktmpdir do |dir|
      assert_raises(ArgumentError) { PlayReleaseHelper.resolve_aab_path(dir) }

      older = File.join(dir, "older.aab")
      newer = File.join(dir, "newer.aab")
      File.write(older, "old")
      File.write(newer, "new")
      now = Time.now
      File.utime(now - 60, now - 60, older)
      File.utime(now, now, newer)

      assert_equal(newer, PlayReleaseHelper.resolve_aab_path(dir))
    end
  end
end
