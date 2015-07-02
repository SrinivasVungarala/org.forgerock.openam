/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2014-2015 ForgeRock AS.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

module.exports = function (grunt) {
    grunt.initConfig({
        // please update environment variable OPENAM_VERSION after realise, for fix cache issue
        // you can use version value from main pom file, ex. 13.0.0-SNAPSHOT
        buildNumber: process.env.OPENAM_VERSION,
        destination: process.env.OPENAM_HOME,
        forgerockui: process.env.FORGEROCK_UI_SRC,
        replace: {
            html: {
                src: ['src/main/resources/index.html'],
                dest: '<%= destination %>/policyEditor/index.html',
                replacements: [{
                    from: '${version}',
                    to:  '<%= buildNumber %>'
                }]
            },
            style: {
                src: ['src/main/resources/css/styles.less'],
                dest: '<%= destination %>/policyEditor/css/styles.less',
                replacements: [{
                    from: '${version}',
                    to:  '<%= buildNumber %>'
                }]
            },
            test: {
                // temporary fix for test
                src: ['src/main/resources/css/styles.less'],
                dest: '<%= destination %>/../www/css/styles.less',
                replacements: [{
                    from: '?v=@{openam-version}',
                    to:  ''
                }]
            }
        },
        sync: {
            source_to_test: {
                files: [
                    {
                        cwd: 'target/dependency',
                        src: ['**'],
                        dest: 'target/www'
                    },
                    {
                        cwd: 'test/libs',
                        src: ['**'],
                        dest: '../../target/www/libs'
                    },
                    {
                        cwd: 'src/main/js',
                        src: ['**'],
                        dest: 'target/www'
                    },
                    {
                        cwd: 'src/main/resources',
                        src: ['**'],
                        dest: 'target/www'
                    },
                    {
                        cwd: 'src/test/resources',
                        src: ['**'],
                        dest: 'target/test'
                    },
                    {
                        cwd: 'src/test/js',
                        src: ['**'],
                        dest: 'target/test'
                    }
                ],
                verbose: true
            },

            source_css_to_test: {
                files: [
                    {
                        cwd: 'target/www',
                        src: ['css/**/*.css'],
                        dest: 'target/test'
                    }
                ],
                verbose: true
            },

            source_to_tomcat: {
                files: [
                    {
                        cwd: '<%= forgerockui %>/forgerock-ui-commons/src/main/js',
                        src: ['**/*'],
                        dest: '<%= destination %>/policyEditor'
                    },
                    {
                        cwd: '<%= forgerockui %>/forgerock-ui-commons/src/main/resources',
                        src: ['**/*'],
                        dest: '<%= destination %>/policyEditor'
                    },
                    {
                        cwd: 'src/main/resources',
                        src: ['**/*'],
                        dest: '<%= destination %>/policyEditor'
                    },
                    {
                        cwd: 'src/main/js',
                        src: ['**/*'],
                        dest: '<%= destination %>/policyEditor'
                    },
                    {
                        cwd: 'target/test',
                        src: ['**'],
                        dest: '<%= destination %>/../test'
                    },
                    {
                        cwd: 'target/www',
                        src: ['**'],
                        dest: '<%= destination %>/../www'
                    }
                ],
                verbose: true
            }
        },
        watch: {
            editor: {
                files: [
                    '<%= forgerockui %>/forgerock-ui-commons/src/main/js/**',
                    '<%= forgerockui %>/forgerock-ui-commons/src/main/resources/**',
                    'src/main/js/**',
                    'src/main/resources/**',
                    'src/test/js/**',
                    'src/test/resources/**'
                ],
                tasks: ['sync', 'replace', 'qunit']
            }
        },

        qunit: {
            all: ['target/test/qunit.html']
        },

        notify_hooks: {
            options: {
                enabled: true,
                title: "OpenAM Policy Editor"
            }
        }
    });

    grunt.loadNpmTasks('grunt-contrib-qunit');
    grunt.loadNpmTasks('grunt-contrib-watch');
    grunt.loadNpmTasks('grunt-notify');
    grunt.loadNpmTasks('grunt-sync');
    grunt.loadNpmTasks('grunt-text-replace');

    grunt.task.run('notify_hooks');

    grunt.registerTask('default', [
        'sync:source_to_test',
        'sync:source_css_to_test',
        'sync:source_to_tomcat',
        'replace',
        'qunit',
        'watch']);
};
